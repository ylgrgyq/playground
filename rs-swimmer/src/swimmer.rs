use crate::endpoint::{Endpoint, EndpointGroup};
use std::io::SeekFrom::End;
use std::time::{SystemTime, Duration};
use std::collections::HashSet;
use std::option::{Iter, IntoIter};

trait EndpointChangeListener {
    fn on_endpoint_changed(endpoint: Endpoint);
}

struct SwimmerStateMaintainer {
    self_endpoint: Endpoint,
    endpoint_group: EndpointGroup,
    ping_interval: Duration,
}

impl SwimmerStateMaintainer {
    fn new(name: &str, address: &str) -> SwimmerStateMaintainer {
        let self_endpoint = Endpoint::new(name, address);
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_endpoint_to_group(self_endpoint.clone());
        SwimmerStateMaintainer {
            self_endpoint,
            endpoint_group,
            ping_interval: Duration::from_secs(10),
        }
    }

    pub fn join<T>(&mut self, endpoints: T)
    where T: IntoIterator<Item=Endpoint> {
        for endpoint in endpoints {
            let address = endpoint.get_address();
            if self.endpoint_group.add_endpoint_to_group(endpoint) {
                self.send_join(&address);
            }
        }
    }

    pub fn shutdown(&self) {}

    fn start() {}

    pub fn get_endpoints(&self) -> HashSet<Endpoint> {
        self.endpoint_group.get_endpoints()
    }

    fn send_join(&self, address: &String) {

    }

    fn ping(&self) {}

    fn ping_req(&self) {}
}

struct Swimmer {
    state: SwimmerStateMaintainer,
}

impl Swimmer {
    fn new(name: &str, address: &str) -> Swimmer {
        let self_endpoint = Endpoint::new(name, address);
        let mut endpoint_group = EndpointGroup::new();
        endpoint_group.add_endpoint_to_group(self_endpoint.clone());
        Swimmer {
            state: SwimmerStateMaintainer {
                self_endpoint,
                endpoint_group,
                ping_interval: Duration::from_secs(10),
            },
        }
    }

    pub fn join(&mut self, endpoint: Endpoint) -> HashSet<Endpoint> {
        self.state.join(vec![endpoint].into_iter());
        self.get_endpoints()
    }

    pub fn join_endpoints<T>(&mut self, endpoints: T) -> HashSet<Endpoint>
    where T: IntoIterator<Item=Endpoint> {
        self.state.join(endpoints);
        self.get_endpoints()
    }

    pub fn get_endpoints(&self) -> HashSet<Endpoint> {
        self.state.get_endpoints()
    }

    pub fn add_endpoint_changed_listener(&self) {}

    pub fn shutdown(&self) {
        self.state.shutdown();
    }
}