use std::collections::HashSet;
use crate::endpoint::{Endpoint, EndpointGroup, EndpointWithState, EndpointStatus, AliveEndpoint, SuspectEndpoint, DeadEndpoint};
use crate::schedule::{Scheduler};
use std::time::{Duration, SystemTime};
use core::mem;
use std::borrow::{Borrow, BorrowMut};
use crate::state::Command::Ping;

#[derive(Debug)]
struct JoinEndpoint {
    from: String,
    name: String,
    address: String,
    incarnation: u32,
    status: EndpointStatus,
}

#[derive(Debug)]
enum Command {
    // from, target_address, known endpoints
    Join(String, String, Vec<JoinEndpoint>),
    // from, knwon_endpoints
    JoinResponse(String, Vec<JoinEndpoint>),
    // from, target_address
    Ping(String, String),
    // from, target_address
    Ack(String, String),
    // from, target_address
    PingReq(String, String),
    // from, suspect_endpoint_name
    Suspect(String, String),
}

#[derive(Debug, Default)]
struct Ready {
    event_box: Vec<Command>,
    changed_endpoints: Vec<Endpoint>,
}

impl Ready {
    fn add_changed_endpoints(&mut self, endpoint: EndpointWithState) {
        self.changed_endpoints.push(endpoint.clone_endpoint());
    }

    fn add_output_event(&mut self, event: Command) {
        self.event_box.push(event)
    }
}

#[derive(Debug, Default)]
struct SwimmerStateMaintainer {
    name: String,
    endpoint_group: EndpointGroup,
    next_ready: Ready,
    tick_interval: Duration,
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

    pub fn handle_command(&mut self, command: Command) -> Result<(), String> {
        match command {
            // from, target_address, known endpoints
            Command::Join(from, target_Address, other_known_endpoints) => {
                if from == self.name {
                    return Err(format!("handle invalid command from myself."));
                }

                self.merge_endpoints(other_known_endpoints);
                let my_known_endpoints = self.endpoint_group.get_endpoints_iter()
                    .map(|e| {
                        JoinEndpoint {
                            name: e.get_endpoint().get_name().clone(),
                            address: e.get_endpoint().get_address().clone(),
                            incarnation: e.get_incarnation(),
                            status: e.get_status(),
                            from: self.name.clone(),
                        }
                    })
                    .collect();
                let resp = Command::JoinResponse(self.name.clone(), my_known_endpoints);
                self.next_ready.add_output_event(resp);
            }
            Command::JoinResponse(from, stats_to_merge) => {
                if from == self.name {
                    return Err(format!("handle invalid command from myself."));
                }
                self.merge_endpoints(stats_to_merge);
            }
            Command::Ping(from, _) => {
                if from == self.name {
                    return Err(format!("handle invalid command from myself."));
                }
                if let Some(e) = self.endpoint_group.get_mut_endpoint(&from) {
                    let ack = Command::Ack(self.name.clone(), e.get_endpoint().get_address().clone());
                    e.set_last_state_change_time(SystemTime::now());
                    self.next_ready.add_output_event(ack);
                } else {
                    return Err(format!("ping from unknown endpoint with name: {}", from));
                }
            }
            // Todo 是不是挪到上层做，因为这里不改变也不读取状态
            Command::PingReq(from, target_address) => {
                if from == self.name {
                    return Err(format!("handle invalid command from myself."));
                }
                if let Some(e) = self.endpoint_group.get_mut_endpoint(&self.name) {
                    if e.get_endpoint().get_address() == &target_address {
                        let ack = Command::Ack(self.name.clone(), e.get_endpoint().get_address().clone());
                        e.set_last_state_change_time(SystemTime::now());
                        self.next_ready.add_output_event(ack);
                    } else {
                        e.set_ping_req_timeout(SystemTime::now() + self.alive_timeout);
                        self.next_ready.add_output_event(Command::Ping(from, target_address))
                    }
                } else {
                    return Err(format!("ping from unknown endpoint with name: {}", from));
                }
            }
            Command::Ack(from, _) => {
                if let Some(e) = self.endpoint_group.get_mut_endpoint(&from) {
                    e.clear_ping_timeout();
                } else {
                    return Err(format!("ack from unknown endpoint with name: {}", from));
                }
            }
            _ => {}
        }
        Ok(())
    }

    fn merge_endpoints(&mut self, stats_to_merge: Vec<JoinEndpoint>) -> Result<(), String> {
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
                        self.next_ready.add_output_event(Command::Ping(e.get_endpoint().get_name().clone(), e.get_endpoint().get_address().clone()))
                    }
                }
                EndpointStatus::Suspect => {
                    for endpoint_name in v {
                        // endpoint must be found
                        let e = self.endpoint_group.get_endpoint(&endpoint_name).unwrap();
                        // self.next_ready.add_output_event(Command::PingReq(e.get_endpoint().get_address().clone()));
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

    fn gossip(&mut self) {}

    fn broadcast_endpoint_changed(&mut self, endpoint: EndpointWithState) {
        self.next_ready.add_changed_endpoints(endpoint);
    }
}