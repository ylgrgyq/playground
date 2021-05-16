use crate::endpoint::{Endpoint, EndpointGroup, EndpointId, EndpointStatus};
use crate::transport::{Transport};
use std::time::{SystemTime, Duration};
use std::collections::HashSet;
use std::option::{Iter, IntoIter};
use std::sync::{Arc, RwLock, Mutex};
use std::thread::JoinHandle;
use std::cell::Cell;

trait EndpointChangeListener {
    fn on_endpoint_changed(&self, endpoint: &Endpoint);
}

struct SwimmerStateMaintainer {
    self_id: EndpointId,
    endpoint_group: EndpointGroup,
    ping_interval: Duration,
    // transport: T,
}

impl SwimmerStateMaintainer {
    fn new(self_id: EndpointId) -> SwimmerStateMaintainer {
        let self_endpoint = Endpoint::new(self_id.clone(), EndpointStatus::Alive);
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_endpoint_to_group(self_endpoint.clone());
        SwimmerStateMaintainer {
            self_id,
            endpoint_group,
            ping_interval: Duration::from_secs(10),
            // transport,
        }
    }

    fn join<I>(&mut self, endpoint_ids: I)
    where I: IntoIterator<Item=EndpointId> {
        for endpoint_id in endpoint_ids {
            let endpoint = Endpoint::new(endpoint_id.clone(), EndpointStatus::Suspect);
            if self.endpoint_group.add_endpoint_to_group(endpoint) {
                self.send_join(&endpoint_id.address);
                match self.endpoint_group.get_endpoint(&endpoint_id.name) {
                    None => (),
                    Some(endpoint_ref) => self.broadcast_endpoint_changed(endpoint_ref),
                }
            }
        }
    }

    // fn add_endpoint_change_listener(&mut self, listener: L) {
    //     self.endpoint_change_listeners.push(listener)
    // }

    fn shutdown(&self) {}

    fn get_endpoints(&self) -> HashSet<&Endpoint> {
        self.endpoint_group.get_endpoints()
    }

    fn send_join(&self, target_address: &String) {
        // self.transport.ping(
        //     target_address,
        //     &self.self_id,
        //     self.get_endpoints(),
        // );
    }

    fn ping(&self, address: &String) {
        // self.transport.ping(
        //     address,
        //     &self.self_id,
        //     self.get_endpoints(),
        // );
    }

    fn ping_req(&self) {
        // self.transport.ping_req()
    }

    fn broadcast_endpoint_changed(&self, endpoint: &Endpoint) {
        // for listener in self.endpoint_change_listeners.iter() {
        //     listener.on_endpoint_changed(endpoint);
        // }
    }
}

struct Swimmer<L: EndpointChangeListener, T: Transport> {
    self_id: EndpointId,
    stopped: bool,
    endpoint_change_listeners: Arc<RwLock<Vec<L>>>,
    transport: Arc<T>,
    core_thread: Option<JoinHandle<()>>,
}

impl<L: EndpointChangeListener + Send + 'static, T: Transport + Send + Sync + 'static> Swimmer<L, T> {
    pub fn new(name: &str, address: &str, transport: T) -> Swimmer<L, T> {
        Swimmer {
            self_id: EndpointId::new(name, address),
            stopped: false,
            endpoint_change_listeners: Arc::new(RwLock::new(vec![])),
            core_thread: None,
            transport: Arc::new(transport),
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
        let state = SwimmerStateMaintainer::new(self.self_id.clone());
        // let listeners = self.endpoint_change_listeners.clone();
        let transport = self.transport.clone();
        let handle = std::thread::spawn(move || {
            let endpoints = state.get_endpoints();
            transport.clone();
            println!("asdfasdf");
            // for l in listeners.read().unwrap().iter() {
            //     println!("111");
            // }
            loop {

            }



            // std::thread::sleep(self.ping_interval);
        });
        self.core_thread = Some(handle);
    }
}