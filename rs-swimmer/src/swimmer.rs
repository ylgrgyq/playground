use crate::endpoint::{Endpoint, EndpointGroup, EndpointId, EndpointStatus};
use crate::transport::{Transport};
use std::time::{SystemTime, Duration};
use std::collections::HashSet;
use std::option::{Iter, IntoIter};

trait EndpointChangeListener {
    fn on_endpoint_changed(&self, endpoint: &Endpoint);
}

struct SwimmerStateMaintainer<L: EndpointChangeListener> {
    self_endpoint: Endpoint,
    endpoint_group: EndpointGroup,
    ping_interval: Duration,
    endpoint_change_listeners: Vec<L>,
    transport: Transport,
}

impl<L: EndpointChangeListener> SwimmerStateMaintainer<L> {
    fn new(name: &str, address: &str, transport: Transport) -> SwimmerStateMaintainer<L> {
        let self_endpoint = Endpoint::new(EndpointId::new(name, address), EndpointStatus::Alive);
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_endpoint_to_group(self_endpoint.clone());
        SwimmerStateMaintainer {
            self_endpoint,
            endpoint_group,
            ping_interval: Duration::from_secs(10),
            endpoint_change_listeners: vec![],
            transport,
        }
    }

    fn join<T>(&mut self, endpoint_ids: T)
    where T: IntoIterator<Item=EndpointId> {
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

    fn add_endpoint_change_listener(&mut self, listener: L) {
        self.endpoint_change_listeners.push(listener)
    }

    fn shutdown(&self) {}

    fn start() {}

    fn get_endpoints(&self) -> HashSet<&Endpoint> {
        self.endpoint_group.get_endpoints()
    }

    fn send_join(&self, address: &String) {
        self.transport.ping(
            address,
            &self.self_endpoint,
            self.get_endpoints()
        );
    }

    fn ping(&self) {}

    fn ping_req(&self) {}

    fn broadcast_endpoint_changed(&self, endpoint: &Endpoint) {
        for listener in self.endpoint_change_listeners.iter() {
            listener.on_endpoint_changed(endpoint);
        }
    }
}

struct Swimmer<L: EndpointChangeListener> {
    state: SwimmerStateMaintainer<L>,
}

impl<L: EndpointChangeListener> Swimmer<L> {
    pub fn new(name: &str, address: &str, transport: Transport) -> Swimmer<L> {
        Swimmer {
            state: SwimmerStateMaintainer::new(name, address, transport),
        }
    }

    pub fn join(&mut self, endpoint: EndpointId) -> HashSet<&Endpoint> {
        self.state.join(vec![endpoint].into_iter());
        self.get_endpoints()
    }

    pub fn batch_join<T>(&mut self, endpoints: T) -> HashSet<&Endpoint>
    where T: IntoIterator<Item=EndpointId> {
        self.state.join(endpoints);
        self.get_endpoints()
    }

    pub fn get_endpoints(&self) -> HashSet<&Endpoint> {
        self.state.get_endpoints()
    }

    pub fn add_endpoint_change_listener(&mut self, listener: L) {
        self.state.add_endpoint_change_listener(listener)
    }

    pub fn shutdown(&self) {
        self.state.shutdown();
    }
}