use std::collections::{HashSet, HashMap};
use crate::endpoint::{Endpoint, EndpointGroup, EndpointWithState, EndpointStatus, AliveEndpoint, SuspectEndpoint, DeadEndpoint};
use crate::schedule::{Scheduler};
use crate::config::{Config};
use std::time::{Duration, SystemTime};
use core::mem;
use std::borrow::{Borrow, BorrowMut};
use crate::state::Command::{Ping, Suspect};
use std::net::Shutdown::Read;

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

#[derive(Debug)]
struct SwimmerStateMaintainer {
    config: Config,
    endpoint_group: EndpointGroup,
    next_ready: Ready,
    next_probe_time: SystemTime,
    probe_timeout_map: HashMap<String, SystemTime>,
    suspect_timeout_map: HashMap<String, SystemTime>,
}

impl SwimmerStateMaintainer {
    pub fn new(config: Config, incarnation: u32) -> SwimmerStateMaintainer {
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_alive_endpoint(AliveEndpoint {
            incarnation,
            name: config.get_name(),
            address: config.get_address(),
        });
        let next_probe_time = SystemTime::now() + config.get_probe_interval();
        SwimmerStateMaintainer {
            config,
            endpoint_group,
            next_probe_time,
            next_ready: Ready::default(),
            probe_timeout_map: HashMap::new(),
            suspect_timeout_map: HashMap::new(),
        }
    }

    pub fn handle_command(&mut self, command: Command) -> Result<(), String> {
        match command {
            // from, target_address, known endpoints
            Command::Join(from, target_Address, other_known_endpoints) => {
                if from == self.config.get_name() {
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
                            from: self.config.get_name(),
                        }
                    })
                    .collect();
                let resp = Command::JoinResponse(self.config.get_name(), my_known_endpoints);
                self.next_ready.add_output_event(resp);
            }
            Command::JoinResponse(from, stats_to_merge) => {
                if from == self.config.get_name() {
                    return Err(format!("handle invalid command from myself."));
                }
                self.merge_endpoints(stats_to_merge);
            }
            Command::Ping(from, _) => {
                if from == self.config.get_name() {
                    return Err(format!("handle invalid command from myself."));
                }
                if let Some(e) = self.endpoint_group.get_mut_endpoint(&from) {
                    let ack = Command::Ack(self.config.get_name(), e.get_endpoint().get_address().clone());
                    e.set_last_state_change_time(SystemTime::now());
                    self.next_ready.add_output_event(ack);
                } else {
                    return Err(format!("ping from unknown endpoint with name: {}", from));
                }
            }
            // Todo 是不是挪到上层做，因为这里不改变也不读取状态
            Command::PingReq(from, target_address) => {
                if from == self.config.get_name() {
                    return Err(format!("handle invalid command from myself."));
                }
                if let Some(e) = self.endpoint_group.get_mut_endpoint(&self.config.get_name()) {
                    if e.get_endpoint().get_address() == &target_address {
                        let ack = Command::Ack(self.config.get_name(), e.get_endpoint().get_address().clone());
                        e.set_last_state_change_time(SystemTime::now());
                        self.next_ready.add_output_event(ack);
                    } else {
                        e.set_ping_req_timeout(SystemTime::now() + self.config.get_probe_timeout());
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

    pub fn tick(&mut self) {
        self.probe();
        self.handle_timeout();
        self.check_endpoints();
    }

    pub fn ready(&mut self) -> Ready {
        mem::take(&mut self.next_ready)
    }

    fn merge_endpoints(&mut self, endpoints_to_merge: Vec<JoinEndpoint>) -> Result<(), String> {
        for endpoint in endpoints_to_merge {
            match endpoint.status {
                EndpointStatus::Alive => {
                    if self.config.name == endpoint.name {
                        return Err(format!("conflict endpoint name: {}", &self.config.name));
                    }
                    self.endpoint_group.add_alive_endpoint(AliveEndpoint {
                        name: endpoint.name,
                        incarnation: endpoint.incarnation,
                        address: endpoint.address,
                    })?
                }
                EndpointStatus::Dead | EndpointStatus::Suspect => {
                    if self.config.name == endpoint.name {
                        return Err(format!("refute to suspect/dead, i'm alive: {}", &self.config.name));
                    }

                    self.endpoint_group.add_suspect_endpoint(SuspectEndpoint {
                        name: endpoint.name,
                        incarnation: endpoint.incarnation,
                        from: endpoint.from,
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

    pub fn shutdown(&mut self) {
        if let Some(endpoint) = self.endpoint_group.remove_endpoint_from_group(&self.config.name) {
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

    fn probe(&mut self) {
        if SystemTime::now().lt(&self.next_probe_time) {
            return;
        }

        while let Some(name) = self.endpoint_group.next_endpoint_name_to_probe() {
            if let Some(endpoint_to_probe) = self.endpoint_group.get_endpoint(&name) {
                if endpoint_to_probe.get_status() == EndpointStatus::Alive {
                    let ping = Command::Ping(
                        endpoint_to_probe.get_endpoint().get_name().clone(),
                        endpoint_to_probe.get_endpoint().get_address().clone());
                    self.next_ready.add_output_event(ping);
                    self.probe_timeout_map.insert(endpoint_to_probe.get_endpoint().get_name().clone(),
                                                  SystemTime::now() + self.config.get_probe_timeout());
                    return;
                }
            }
        }
    }

    fn handle_timeout(&mut self) {
        let now = SystemTime::now();
        let mut probing_to_delete = Vec::new();
        for (name, timeout) in &self.probe_timeout_map {
            if now.ge(timeout) {
                let group = &mut self.endpoint_group;
                if let Some(endpoint) = group.get_endpoint(name) {
                    group.add_suspect_endpoint(SuspectEndpoint {
                        incarnation: endpoint.get_incarnation(),
                        name: endpoint.get_endpoint().get_name().clone(),
                        from: self.config.name.clone(),
                    });
                }
                self.next_ready.add_output_event(Suspect(self.config.name.clone(), name.clone()));
                probing_to_delete.push(name.clone());
            }
        }
        for name in probing_to_delete {
            self.probe_timeout_map.remove(&name);
        }
    }

    fn check_endpoints(&mut self) {
        let mut dead_endpoint = None;
        for (k, v) in self.endpoint_group.classify_endpoints_by_status() {
            match k {
                EndpointStatus::Alive => {
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