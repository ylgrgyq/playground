use crate::endpoint::{Endpoint, EndpointGroup, EndpointStatus, EndpointWithState};
use crate::transport::{Transport};
use std::time::{SystemTime, Duration, Instant};
use std::collections::{HashSet, HashMap};
use std::option::{Iter, IntoIter};
use std::sync::{Arc, RwLock, Mutex};
use std::thread::JoinHandle;
use std::cell::Cell;
use std::sync::mpsc;
use std::sync::mpsc::RecvTimeoutError;
use futures::SinkExt;
use core::mem;
use std::net::Shutdown::Read;
use std::collections::hash_map::Entry;

trait EndpointChangeListener {
    fn on_endpoint_changed(&self, name: &String, status: &EndpointStatus);
}

enum InputCommand {
    Join(String, String),
    JoinResponse(String),
}

#[derive(Debug)]
enum OutputEvent {
    Ping(String, String, HashSet<Endpoint>),
    PingReq(String),
}

#[derive(Debug, Default)]
struct Ready {
    event_box: Vec<OutputEvent>,
    changed_endpoints: Vec<Endpoint>,
}

impl Ready {
    pub fn take_event_box(&mut self) -> Vec<OutputEvent> {
        mem::take(&mut self.event_box)
    }

    pub fn take_changed_endpoints(&mut self) -> Vec<Endpoint> {
        mem::take(&mut self.changed_endpoints)
    }
}

#[derive(Debug, Default)]
struct SwimmerStateMaintainer {
    name: String,
    endpoint_group: EndpointGroup,
    event_box: Vec<OutputEvent>,
    changed_endpoints: Vec<Endpoint>,
    alive_timeout: Duration,
    suspect_timeout: Duration,
}

impl SwimmerStateMaintainer {
    pub fn new(name: String, address: String) -> SwimmerStateMaintainer {
        let self_endpoint = Endpoint::new(name.clone(), address.clone());
        let self_state = EndpointWithState::new(self_endpoint, 1, EndpointStatus::Alive);
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_endpoint_to_group(self_state);
        SwimmerStateMaintainer {
            name,
            endpoint_group,
            alive_timeout: Duration::from_secs(20),
            suspect_timeout: Duration::from_secs(30),
            ..Default::default()
        }
    }

    pub fn join(&mut self, address: String) {
        self.send_join(&address);
    }

    pub fn left(&mut self, name: String) {
        if let Some(endpoint) = self.endpoint_group.remove_endpoint_from_group(&name) {
            self.broadcast_endpoint_changed(endpoint);
        }
    }

    pub fn tick(&mut self) {
        self.update_endpoint_status();
        self.check_endpoints();
    }

    pub fn ready(&mut self) -> Ready {
        Ready {
            event_box: mem::take(&mut self.event_box),
            changed_endpoints: mem::take(&mut self.changed_endpoints),
        }
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
        self.event_box.push(OutputEvent::Ping(self.name.clone(), target_address.clone(), self.clone_endpoints()))
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
        let mut ebox = vec![];
        let mut dead_endpoint = None;
        for (k, v) in self.endpoint_group.classify_endpoints_by_status() {
            match k {
                EndpointStatus::Alive => {
                    let known_endpoints = self.clone_endpoints();
                    for endpoint_name in v {
                        // endpoint must be found
                        let e = self.endpoint_group.get_endpoint(&endpoint_name).unwrap();
                        ebox.push(OutputEvent::Ping(e.get_endpoint().get_name().clone(), e.get_endpoint().get_address().clone(), known_endpoints.clone()))
                    }
                }
                EndpointStatus::Suspect => {
                    for endpoint_name in v {
                        // endpoint must be found
                        let e = self.endpoint_group.get_endpoint(&endpoint_name).unwrap();
                        ebox.push(OutputEvent::PingReq(e.get_endpoint().get_address().clone()))
                    }
                }
                EndpointStatus::Dead => {
                    dead_endpoint = Some(v);
                }
            }
        }

        for event in ebox {
            self.event_box.push(event);
        }

        if let Some(names) = dead_endpoint {
            for dead_endpoint_name in names {
                self.endpoint_group.remove_endpoint_from_group(&dead_endpoint_name);
            }
        }
    }

    fn broadcast_endpoint_changed(&mut self, endpoint: EndpointWithState) {
        self.changed_endpoints.push(endpoint.clone_endpoint());
    }
}

// struct Swimmer<L: EndpointChangeListener, T: Transport> {
//     stopped: bool,
//     endpoint_change_listeners: Arc<RwLock<Vec<L>>>,
//     transport: Arc<T>,
//     core_thread: JoinHandle<()>,
//     sender: mpsc::Sender<InputCommand>,
// }
//
// impl<L: EndpointChangeListener + Send + Sync + 'static, T: Transport + Send + Sync + 'static> Swimmer<L, T> {
//     pub fn new(name: &str, address: &str, transport: T) -> Swimmer<L, T> {
//         let endpoint_change_listeners = Arc::new(RwLock::new(vec![]));
//         let transport = Arc::new(transport);
//         let (sender, receiver) = mpsc::channel();
//         let core_thread = Swimmer::start_state_maintainer(
//             String::from(name),
//             String::from(address),
//             endpoint_change_listeners.clone(),
//             transport.clone(),
//             Duration::from_secs(10),
//             receiver,
//         );
//         Swimmer {
//             stopped: false,
//             endpoint_change_listeners,
//             core_thread,
//             transport,
//             sender,
//         }
//     }
//
//     pub fn join(&mut self, name: String, address: String) -> Result<HashSet<&Endpoint>, String> {
//         if self.stopped {
//             return Err(format!("Swimmer has stopped"));
//         }
//
//         self.sender.send(InputCommand::Join(name, address));
//         Ok(self.get_endpoints()?)
//     }
//
//     // pub fn batch_join<I>(&mut self, endpoint_ids: I) -> Result<HashSet<&Endpoint>, String>
//     // where I: IntoIterator<Item=EndpointId> {
//     //     if self.stopped {
//     //         return Err(format!("Swimmer has stopped"));
//     //     }
//     //
//     //     for id in endpoint_ids {
//     //         self.sender.send(InputCommand::Join(id));
//     //     }
//     //     Ok(self.get_endpoints()?)
//     // }
//
//     pub fn get_endpoints(&self) -> Result<HashSet<&Endpoint>, String> {
//         if self.stopped {
//             return Err(format!("Swimmer has stopped"));
//         }
//
//         // Ok(self.state.get_endpoints())
//         Err(String::from("asdf"))
//     }
//
//     pub fn add_endpoint_change_listener(&mut self, listener: L) -> Result<(), String> {
//         if self.stopped {
//             return Err(format!("Swimmer has stopped"));
//         }
//
//         self.endpoint_change_listeners.write().unwrap().push(listener);
//         Ok(())
//     }
//
//     fn start_state_maintainer(name: String,
//                               address: String,
//                               listeners: Arc<RwLock<Vec<L>>>,
//                               transport: Arc<T>,
//                               ping_interval: Duration,
//                               receiver: mpsc::Receiver<InputCommand>)
//                               -> JoinHandle<()> {
//         let mut state = SwimmerStateMaintainer::new(name.clone(), address);
//         let handle = std::thread::spawn(move || {
//             let on_ready = |state: &mut SwimmerStateMaintainer| {
//                 let mut ready = state.ready();
//
//                 for event in ready.take_event_box() {
//                     match event {
//                         OutputEvent::Ping(name, addr, ends) => {
//                             transport.ping(&name, &addr, ends)
//                         }
//                         OutputEvent::PingReq(addr) => {
//                             // transport.ping_req(&self_id, &addr, ends)
//                         }
//                     }
//                 }
//
//                 for endpoint in ready.take_changed_endpoints() {
//                     for l in listeners.read().unwrap().iter() {
//                         // l.on_endpoint_changed(&endpoint.get_id(), &endpoint.get_status())
//                     }
//                 }
//             };
//
//             let mut t = Instant::now();
//             let mut timeout = ping_interval;
//             loop {
//                 match receiver.recv_timeout(timeout) {
//                     Ok(InputCommand::Join(name, address)) => {
//                         state.join(name, address)
//                     }
//                     Err(RecvTimeoutError::Timeout) => (),
//                     Err(RecvTimeoutError::Disconnected) => {
//                         state.shutdown();
//                         return;
//                     }
//                 }
//
//                 let d = t.elapsed();
//                 t = Instant::now();
//                 if d >= timeout {
//                     timeout = ping_interval;
//                     state.tick();
//                 } else {
//                     timeout -= d;
//                 }
//
//                 on_ready(&mut state);
//             }
//         });
//         handle
//     }
// }
//
// #[cfg(test)]
// mod tests {
//     use super::*;
//
//     #[test]
//     fn basic_add_endpoint() {
//
//     }
// }