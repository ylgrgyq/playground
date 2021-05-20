use crate::endpoint::{Endpoint, EndpointGroup, EndpointId, EndpointStatus};
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
    fn on_endpoint_changed(&self, id: &EndpointId, status: &EndpointStatus);
}

enum InputCommand {
    Join(EndpointId),
}

#[derive(Debug)]
enum OutputEvent {
    Ping(String, HashSet<Endpoint>),
    PingReq(String),
}

impl OutputEvent {
    fn create_ping(target_address: String, known_endpoints: HashSet<Endpoint>) -> OutputEvent {
        OutputEvent::Ping(target_address, known_endpoints)
    }

    fn create_ping_req(target_address: String) -> OutputEvent {
        OutputEvent::PingReq(target_address)
    }
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
    self_id: EndpointId,
    endpoint_group: EndpointGroup,
    event_box: Vec<OutputEvent>,
    changed_endpoints: Vec<Endpoint>,
    alive_timeout: Duration,
    suspect_timeout: Duration,
}

impl SwimmerStateMaintainer {
    pub fn new(self_id: EndpointId) -> SwimmerStateMaintainer {
        let self_endpoint = Endpoint::new(self_id.clone(), EndpointStatus::Alive);
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_endpoint_to_group(self_endpoint.clone());
        SwimmerStateMaintainer {
            self_id,
            endpoint_group,
            alive_timeout: Duration::from_secs(20),
            suspect_timeout: Duration::from_secs(30),
            ..Default::default()
        }
    }

    pub fn join(&mut self, endpoint_id: EndpointId) {
        let endpoint = Endpoint::new(endpoint_id.clone(), EndpointStatus::Suspect);
        if self.endpoint_group.add_endpoint_to_group(endpoint.clone()) {
            self.send_join(&endpoint_id.address);
            self.broadcast_endpoint_changed(&endpoint);
        }
    }

    fn tick(&mut self) {
        self.update_endpoint_status();
        self.endpoint_group.clear_dead_endpoints();
        self.check_endpoints();
    }

    fn shutdown(&self) {}

    fn get_endpoints_iter(&self) -> impl Iterator<Item=&Endpoint> {
        self.endpoint_group.get_endpoints_iter()
    }

    fn get_mut_endpoints_iter(&mut self) -> impl Iterator<Item=&mut Endpoint> {
        let e = &mut self.endpoint_group;
        e.get_mut_endpoints_iter()
    }

    fn clone_endpoints(&self) -> HashSet<Endpoint> {
        self.endpoint_group.get_endpoints_iter()
            .into_iter().map(|e| e.clone()).collect()
    }

    fn send_join(&mut self, target_address: &String) {
        self.event_box.push(OutputEvent::Ping(target_address.clone(), self.clone_endpoints()))
    }

    fn ping(&mut self, target_address: &String) {
        self.event_box.push(OutputEvent::Ping(target_address.clone(), self.clone_endpoints()))
    }

    fn ping_req(&mut self, target_address: &String) {
        self.event_box.push(OutputEvent::PingReq(target_address.clone()))
    }

    fn update_endpoint_status(&mut self) {
        let now = SystemTime::now();
        let group = &mut self.endpoint_group;
        for endpoint in group.get_mut_endpoints_iter() {
            let inactive_duration = endpoint.get_inactive_duration(&now);
            if inactive_duration > self.suspect_timeout {
                endpoint.set_status(EndpointStatus::Dead)
            } else if inactive_duration > self.alive_timeout {
                endpoint.set_status(EndpointStatus::Suspect)
            }
        }
    }

    fn check_endpoints(&mut self) {
        let mut classified_endpoint = self.get_classify();


        let mut ebox = vec![];
        for (k, v) in classified_endpoint {
            match k {
                EndpointStatus::Alive => {
                    let ss = self.clone_endpoints();
                    for e in v {
                        ebox.push(OutputEvent::Ping(e.get_address().clone(), ss.clone()))
                    }
                }
                EndpointStatus::Suspect => {
                    for e in v {
                        ebox.push(OutputEvent::PingReq(e.get_address().clone()))
                    }
                }
                EndpointStatus::Dvdxead => {
                    // let mut_group = &mut self.endpoint_group;
                    // for e in v {
                    //     mut_group.remove_endpoint_from_group(e.get_name());
                    // }
                }
            }
        }
    }

    fn get_classify(&self) -> HashMap<EndpointStatus, Vec<&Endpoint>> {
        self.endpoint_group.classify()
    }

    fn broadcast_endpoint_changed(&mut self, endpoint: &Endpoint) {
        let endpoint = Endpoint::new(endpoint.get_id().clone(), endpoint.get_status());
        self.changed_endpoints.push(endpoint);
    }

    fn ready(&mut self) -> Ready {
        Ready {
            event_box: mem::take(&mut self.event_box),
            changed_endpoints: mem::take(&mut self.changed_endpoints),
        }
    }
}

struct Swimmer<L: EndpointChangeListener, T: Transport> {
    self_id: EndpointId,
    stopped: bool,
    endpoint_change_listeners: Arc<RwLock<Vec<L>>>,
    transport: Arc<T>,
    core_thread: JoinHandle<()>,
    sender: mpsc::Sender<InputCommand>,
}

impl<L: EndpointChangeListener + Send + Sync + 'static, T: Transport + Send + Sync + 'static> Swimmer<L, T> {
    pub fn new(name: &str, address: &str, transport: T) -> Swimmer<L, T> {
        let self_id = EndpointId::new(name, address);
        let endpoint_change_listeners = Arc::new(RwLock::new(vec![]));
        let transport = Arc::new(transport);
        let (sender, receiver) = mpsc::channel();
        let core_thread = Swimmer::start_state_maintainer(
            self_id,
            endpoint_change_listeners.clone(),
            transport.clone(),
            Duration::from_secs(10),
            receiver,
        );
        Swimmer {
            self_id: EndpointId::new(name, address),
            stopped: false,
            endpoint_change_listeners,
            core_thread,
            transport,
            sender,
        }
    }

    pub fn join(&mut self, endpoint_id: EndpointId) -> Result<HashSet<&Endpoint>, String> {
        if self.stopped {
            return Err(format!("Swimmer: {} has stopped", &self.self_id));
        }

        self.sender.send(InputCommand::Join(endpoint_id));
        Ok(self.get_endpoints()?)
    }

    pub fn batch_join<I>(&mut self, endpoint_ids: I) -> Result<HashSet<&Endpoint>, String>
    where I: IntoIterator<Item=EndpointId> {
        if self.stopped {
            return Err(format!("Swimmer: {} has stopped", &self.self_id));
        }

        for id in endpoint_ids {
            self.sender.send(InputCommand::Join(id));
        }
        Ok(self.get_endpoints()?)
    }

    pub fn get_endpoints(&self) -> Result<HashSet<&Endpoint>, String> {
        if self.stopped {
            return Err(format!("Swimmer: {} has stopped", &self.self_id));
        }

        // Ok(self.state.get_endpoints())
        Err(String::from("asdf"))
    }

    pub fn add_endpoint_change_listener(&mut self, listener: L) -> Result<(), String> {
        if self.stopped {
            return Err(format!("Swimmer: {} has stopped", &self.self_id));
        }

        self.endpoint_change_listeners.write().unwrap().push(listener);
        Ok(())
    }

    fn start_state_maintainer(self_id: EndpointId,
                              listeners: Arc<RwLock<Vec<L>>>,
                              transport: Arc<T>,
                              ping_interval: Duration,
                              receiver: mpsc::Receiver<InputCommand>)
                              -> JoinHandle<()> {
        let mut state = SwimmerStateMaintainer::new(self_id.clone());
        let handle = std::thread::spawn(move || {
            let on_ready = |state: &mut SwimmerStateMaintainer| {
                let mut ready = state.ready();

                for event in ready.take_event_box() {
                    match event {
                        OutputEvent::Ping(addr, ends) => {
                            transport.ping(&self_id, &addr, ends)
                        }
                        OutputEvent::PingReq(addr) => {
                            // transport.ping_req(&self_id, &addr, ends)
                        }
                    }
                }

                for endpoint in ready.take_changed_endpoints() {
                    for l in listeners.read().unwrap().iter() {
                        l.on_endpoint_changed(&endpoint.get_id(), &endpoint.get_status())
                    }
                }
            };

            let mut t = Instant::now();
            let mut timeout = ping_interval;
            loop {
                match receiver.recv_timeout(timeout) {
                    Ok(InputCommand::Join(new_endpoint_id)) => {
                        state.join(new_endpoint_id)
                    }
                    Err(RecvTimeoutError::Timeout) => (),
                    Err(RecvTimeoutError::Disconnected) => {
                        state.shutdown();
                        return;
                    }
                }

                let d = t.elapsed();
                t = Instant::now();
                if d >= timeout {
                    timeout = ping_interval;
                    state.tick();
                } else {
                    timeout -= d;
                }

                on_ready(&mut state);
            }
        });
        handle
    }
}