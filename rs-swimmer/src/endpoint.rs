use std::collections::{HashSet, HashMap};
use std::time::SystemTime;
use std::cell::RefCell;
use std::collections::hash_map::RandomState;
use std::hash::{Hash, Hasher};

#[derive(Debug, Copy, Clone, PartialEq, Eq, Hash)]
pub enum EndpointStatus {
    Alive,
    Suspect,
    Dead,
    Left,
}

#[derive(Debug, Clone, Eq)]
pub struct Endpoint {
    name: String,
    address: String,
    status: RefCell<EndpointStatus>,
    last_active_time: RefCell<SystemTime>,
}

impl Hash for Endpoint {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.name.hash(state);
        self.address.hash(state);
    }
}

impl PartialEq for Endpoint {
    fn eq(&self, other: &Self) -> bool {
        return self.name == other.name;
    }
}

impl Endpoint {
    pub fn new(name: &str, address: &str) -> Endpoint {
        Endpoint {
            name: String::from(name),
            address: String::from(address),
            status: RefCell::new(EndpointStatus::Alive),
            last_active_time: RefCell::new(SystemTime::now()),
        }
    }

    pub fn get_status(&self) -> EndpointStatus {
        self.status.borrow().clone()
    }

    pub fn get_address(&self) -> String {
        self.address.clone()
    }

    pub fn get_name(&self) -> String {
        self.name.clone()
    }
}

pub struct EndpointGroup {
    group: HashMap<String, Endpoint>,
}

impl EndpointGroup {
    pub fn new() -> EndpointGroup {
        EndpointGroup { group: HashMap::new() }
    }

    pub fn add_endpoint_to_group(&mut self, endpoint: Endpoint) -> bool {
        if self.contains(&endpoint.name) {
            return false;
        }

        let group = &mut self.group;
        let name = &endpoint.name;
        group.insert(name.clone(), endpoint);
        true
    }

    pub fn remove_endpoint_from_group(&mut self, name: &String) -> bool {
        let group = &mut self.group;
        match group.remove(name) {
            None => false,
            Some(_) => true
        }
    }

    pub fn contains(&self, name: &String) -> bool {
        self.group.contains_key(name)
    }

    pub fn get_endpoints(&self) -> HashSet<&Endpoint> {
        let mut endpoints = HashSet::new();
        let group = &self.group;
        group.values().for_each(|e| -> () {
            endpoints.insert(e);
        });
        endpoints
    }

    pub fn get_endpoint(&self, name: &String) -> Option<&Endpoint> {
        let group = &self.group;
        group.get(name)
    }

    pub fn update_active_timestamp(&self, name: &String) -> bool {
        match self.group.get(name) {
            None => false,
            Some(endpoint) => {
                *endpoint.last_active_time.borrow_mut() = SystemTime::now();
                true
            }
        }
    }

    pub fn update_status(&self, name: &String, new_status: EndpointStatus) -> bool {
        match self.group.get(name) {
            None => false,
            Some(endpoint) => {
                *endpoint.status.borrow_mut() = new_status;
                true
            }
        }
    }

    pub fn len(&self) -> usize {
        self.group.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn basic_add_endpoint() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let endpoint2 = Endpoint::new("endpoint2", "127.0.0.1");
        let mut group = EndpointGroup::new();
        assert!(group.add_endpoint_to_group(endpoint1.clone()));
        assert!(group.contains(&endpoint1.name));
        assert_eq!(1, group.len());

        assert!(group.add_endpoint_to_group(endpoint2.clone()));
        assert!(group.contains(&endpoint1.name));
        assert!(group.contains(&endpoint2.name));
        assert_eq!(2, group.len());
    }

    #[test]
    fn add_duplicate_endpoint() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let endpoint2 = Endpoint::new("endpoint1", "127.0.0.1");
        let mut group = EndpointGroup::new();
        assert!(group.add_endpoint_to_group(endpoint1.clone()));
        assert!(!group.add_endpoint_to_group(endpoint2));
        assert!(group.contains(&endpoint1.name));
        assert_eq!(1, group.len());
    }

    #[test]
    fn remove_endpoint() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let endpoint2 = Endpoint::new("endpoint2", "127.0.0.1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());
        group.add_endpoint_to_group(endpoint2.clone());

        assert!(group.remove_endpoint_from_group(&endpoint1.name));
        assert!(!group.contains(&endpoint1.name));
        assert_eq!(1, group.len());

        assert!(group.remove_endpoint_from_group(&endpoint2.name));
        assert!(!group.contains(&endpoint2.name));
        assert_eq!(0, group.len());

        assert!(!group.remove_endpoint_from_group(&String::from("not exists endpoint")));
    }

    #[test]
    fn update_active_timestamp() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());

        assert!(group.update_active_timestamp(&endpoint1.name));
        assert!(group.get_endpoint(&endpoint1.name).unwrap().last_active_time > endpoint1.last_active_time);
        assert!(!group.update_active_timestamp(&String::from("not exists endpoint")));
    }

    #[test]
    fn update_status() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());

        assert!(group.update_status(&endpoint1.name, EndpointStatus::Dead));

        assert_eq!(EndpointStatus::Dead, group.get_endpoint(&endpoint1.name).unwrap().get_status());

        assert_ne!(endpoint1.get_status(), group.get_endpoint(&endpoint1.name).unwrap().get_status());

        assert!(!group.update_status(&String::from("not exists endpoint"), EndpointStatus::Dead));
    }
}




