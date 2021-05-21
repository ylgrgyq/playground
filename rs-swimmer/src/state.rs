use std::collections::HashSet;
use crate::endpoint::{Endpoint, EndpointGroup, EndpointWithState, EndpointStatus, AliveEndpoint, SuspectEndpoint, DeadEndpoint};
use std::time::{Duration, SystemTime};
use core::mem;

struct JoinEndpointResponse {
    from: String,
    name: String,
    address: String,
    incarnation: u32,
    status: EndpointStatus,
}

enum InputCommand {
    Join(String, String),
    JoinResponse(String),
}

#[derive(Debug)]
enum OutputEvent {
    // from, target_address, known endpoints
    Join(String, String, HashSet<Endpoint>),
    // from, target_address
    Ping(String, String),
    //
    PingReq(String),
    // from, suspect_endpoint_name
    Suspect(String, String),
}

#[derive(Debug, Default)]
struct Ready {
    event_box: Vec<OutputEvent>,
    changed_endpoints: Vec<Endpoint>,
}

impl Ready {
    fn add_changed_endpoints(&mut self, endpoint: EndpointWithState) {
        self.changed_endpoints.push(endpoint.clone_endpoint());
    }

    fn add_output_event(&mut self, event: OutputEvent) {
        self.event_box.push(event)
    }
}

#[derive(Debug, Default)]
struct SwimmerStateMaintainer {
    name: String,
    endpoint_group: EndpointGroup,
    next_ready: Ready,
    alive_timeout: Duration,
    suspect_timeout: Duration,
}

impl SwimmerStateMaintainer {
    pub fn new(name: String, address: String, incarnation: u32) -> SwimmerStateMaintainer {
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_alive_endpoint(AliveEndpoint {
            incarnation,
            name: name.clone(),
            address,
        });
        SwimmerStateMaintainer {
            name,
            endpoint_group,
            alive_timeout: Duration::from_secs(20),
            suspect_timeout: Duration::from_secs(30),
            ..Default::default()
        }
    }

    pub fn handle_join_response(&mut self, stats_to_merge: Vec<JoinEndpointResponse>) -> Result<(), String> {
        for joined_endpoint in stats_to_merge {
            match joined_endpoint.status {
                EndpointStatus::Alive => {
                    if self.name == joined_endpoint.name {
                        return Err(format!("conflict endpoint name: {}", self.name));
                    }
                    self.endpoint_group.add_alive_endpoint(AliveEndpoint {
                        name: joined_endpoint.name,
                        incarnation: joined_endpoint.incarnation,
                        address: joined_endpoint.address,
                    })?
                }
                EndpointStatus::Dead | EndpointStatus::Suspect => {
                    if self.name == joined_endpoint.name {
                        return Err(format!("refute to suspect, i'm alive: {}", self.name));
                    }

                    self.endpoint_group.add_suspect_endpoint(SuspectEndpoint {
                        name: joined_endpoint.name,
                        incarnation: joined_endpoint.incarnation,
                        from: joined_endpoint.from,
                    })?
                    // 广播 suspect
                }
            }
        }
        Ok(())
    }

    pub fn handle_left_notify(&mut self, name: String) {
        if let Some(endpoint) = self.endpoint_group.remove_endpoint_from_group(&name) {
            self.broadcast_endpoint_changed(endpoint);
        }
    }

    pub fn tick(&mut self) {
        self.update_endpoint_status();
        self.check_endpoints();
    }

    pub fn ready(&mut self) -> Ready {
        mem::take(&mut self.next_ready)
    }

    pub fn shutdown(&mut self) {
        if let Some(endpoint) = self.endpoint_group.remove_endpoint_from_group(&self.name) {
            self.broadcast_endpoint_changed(endpoint)
        }
    }

    fn get_endpoints_iter(&self) -> impl Iterator<Item=&EndpointWithState> {
        self.endpoint_group.get_endpoints_iter()
    }

    fn get_mut_endpoints_iter(&mut self) -> impl Iterator<Item=&mut EndpointWithState> {
        let e = &mut self.endpoint_group;
        e.get_mut_endpoints_iter()
    }

    fn clone_endpoints(&self) -> HashSet<Endpoint> {
        self.endpoint_group.get_endpoints_iter()
            .into_iter()
            .map(|e| e.clone_endpoint())
            .collect()
    }

    fn send_join(&mut self, target_address: &String) {
        self.next_ready.add_output_event(OutputEvent::Join(self.name.clone(), target_address.clone(), self.clone_endpoints()));
    }

    fn update_endpoint_status(&mut self) {
        let now = SystemTime::now();
        let group = &mut self.endpoint_group;
        for endpoint in group.get_mut_endpoints_iter() {
            // let inactive_duration = endpoint.get_inactive_duration(&now);
            // if inactive_duration > self.suspect_timeout {
            //     endpoint.set_status(EndpointStatus::Dead)
            // } else if inactive_duration > self.alive_timeout {
            //     endpoint.set_status(EndpointStatus::Suspect)
            // }
        }
    }

    fn check_endpoints(&mut self) {
        let mut dead_endpoint = None;
        for (k, v) in self.endpoint_group.classify_endpoints_by_status() {
            match k {
                EndpointStatus::Alive => {
                    let known_endpoints = self.clone_endpoints();
                    for endpoint_name in v {
                        // endpoint must be found
                        let e = self.endpoint_group.get_endpoint(&endpoint_name).unwrap();
                        self.next_ready.add_output_event(OutputEvent::Ping(e.get_endpoint().get_name().clone(), e.get_endpoint().get_address().clone()))
                    }
                }
                EndpointStatus::Suspect => {
                    for endpoint_name in v {
                        // endpoint must be found
                        let e = self.endpoint_group.get_endpoint(&endpoint_name).unwrap();
                        self.next_ready.add_output_event(OutputEvent::PingReq(e.get_endpoint().get_address().clone()));
                    }
                }
                EndpointStatus::Dead => {
                    dead_endpoint = Some(v);
                }
            }
        }

        if let Some(names) = dead_endpoint {
            for dead_endpoint_name in names {
                self.endpoint_group.remove_endpoint_from_group(&dead_endpoint_name);
            }
        }
    }

    fn broadcast_endpoint_changed(&mut self, endpoint: EndpointWithState) {
        self.next_ready.add_changed_endpoints(endpoint);
    }
}