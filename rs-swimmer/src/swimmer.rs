use crate::endpoint::{Endpoint, EndpointGroup, EndpointId, EndpointStatus};
use crate::transport::{Transport};
use std::time::{SystemTime, Duration};
use std::collections::HashSet;
use std::option::{Iter, IntoIter};
use std::sync::{Arc, RwLock, Mutex};
use std::thread::JoinHandle;
use std::cell::Cell;

trait EndpointChangeListener {
    fn on_endpoint_changed(&self, id: &EndpointId, status: &EndpointStatus);
}

enum Message {}

enum OutputEvent {
    Ping(String, HashSet<Endpoint>),
    PingReq(String),
    EndpointChanged(EndpointId, EndpointStatus),
}

struct SwimmerStateMaintainer {
    self_id: EndpointId,
    endpoint_group: RwLock<EndpointGroup>,
    event_box: Vec<OutputEvent>,
}

impl SwimmerStateMaintainer {
    fn new(self_id: EndpointId) -> SwimmerStateMaintainer {
        let self_endpoint = Endpoint::new(self_id.clone(), EndpointStatus::Alive);
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_endpoint_to_group(self_endpoint.clone());
        SwimmerStateMaintainer {
            self_id,
            endpoint_group: RwLock::new(endpoint_group),
            event_box: vec![],
        }
    }

    fn join<I>(&mut self, endpoint_ids: I)
    where I: IntoIterator<Item=EndpointId> {
        for endpoint_id in endpoint_ids {
            let endpoint = Endpoint::new(endpoint_id.clone(), EndpointStatus::Suspect);
            if self.endpoint_group.write().unwrap().add_endpoint_to_group(endpoint.clone()) {
                self.send_join(&endpoint_id.address);
                self.broadcast_endpoint_changed(&endpoint);
            }
        }
    }

    fn shutdown(&self) {}

    fn get_endpoints(&self) -> HashSet<Endpoint> {
        self.endpoint_group.read().unwrap().get_endpoints()
    }

    fn send_join(&mut self, target_address: &String) {
        self.event_box.push(OutputEvent::Ping(target_address.clone(), self.get_endpoints()))
    }

    fn ping(&mut self, target_address: &String) {
        self.event_box.push(OutputEvent::Ping(target_address.clone(), self.get_endpoints()))
    }

    fn ping_req(&mut self, target_address: &String) {
        self.event_box.push(OutputEvent::PingReq(target_address.clone()))
    }

    fn broadcast_endpoint_changed(&mut self, endpoint: &Endpoint) {
        let event = OutputEvent::EndpointChanged(endpoint.get_id().clone(), endpoint.get_status());
        self.event_box.push(event);
    }

    fn clear_event_box(&mut self) -> Vec<OutputEvent> {
        let mut new_box = vec![];
        std::mem::swap(&mut self.event_box, &mut new_box);
        new_box
    }
}

struct Swimmer<L: EndpointChangeListener, T: Transport> {
    self_id: EndpointId,
    stopped: bool,
    endpoint_change_listeners: Arc<RwLock<Vec<L>>>,
    transport: Arc<T>,
    core_thread: Option<JoinHandle<()>>,
    ping_interval: Duration,
}

impl<L: EndpointChangeListener + Send + 'static, T: Transport + Send + Sync + 'static> Swimmer<L, T> {
    pub fn new(name: &str, address: &str, transport: T) -> Swimmer<L, T> {
        Swimmer {
            self_id: EndpointId::new(name, address),
            stopped: false,
            endpoint_change_listeners: Arc::new(RwLock::new(vec![])),
            core_thread: None,
            transport: Arc::new(transport),
            ping_interval: Duration::from_secs(10),
        }
    }

    pub fn join(&mut self, endpoint: EndpointId) -> Result<HashSet<&Endpoint>, String> {
        if self.stopped {
            return Err(format!("Swimmer: {} has stopped", &self.self_id));
        }

        // self.state.join(vec![endpoint].into_iter());
        Ok(self.get_endpoints()?)
    }

    pub fn batch_join<I>(&mut self, endpoints: I) -> Result<HashSet<&Endpoint>, String>
    where I: IntoIterator<Item=EndpointId> {
        if self.stopped {
            return Err(format!("Swimmer: {} has stopped", &self.self_id));
        }

        // self.state.join(endpoints);
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

    pub fn shutdown(&mut self) {
        if self.stopped {
            return;
        }

        self.stopped = true;
        // self.state.shutdown();
    }

    fn start(&mut self) {
        let mut state = SwimmerStateMaintainer::new(self.self_id.clone());
        let self_id = self.self_id.clone();
        let listeners = self.endpoint_change_listeners.clone();
        let ping_interval = self.ping_interval;
        let transport = self.transport.clone();
        let handle = std::thread::spawn(move || {
            let endpoints = state.get_endpoints();
            transport.clone();
            println!("asdfasdf");

            loop {
                let event_box = state.clear_event_box();
                for event in event_box {
                    match event {
                        OutputEvent::Ping(addr, ends) => {
                            transport.ping(&self_id, &addr, ends)
                        }
                        OutputEvent::PingReq(addr) => {
                            // transport.ping_req(&self_id, &addr, ends)
                        },
                        OutputEvent::EndpointChanged(id, status) => {
                            for l in listeners.read().unwrap().iter() {
                                l.on_endpoint_changed(&id, &status)
                            }
                        },
                    }
                }

                std::thread::sleep(ping_interval);
            }
        });
        self.core_thread = Some(handle);
    }
}