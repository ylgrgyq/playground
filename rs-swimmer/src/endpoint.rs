use std::collections::{HashSet, HashMap};
use std::time::SystemTime;
use std::cell::RefCell;
use std::collections::hash_map::RandomState;
use std::hash::{Hash, Hasher};

#[derive(Debug, Copy, Clone, PartialEq, Eq, Hash)]
enum EndpointStatus {
    Alive,
    Suspect,
    Dead,
    Left,
}

#[derive(Debug, Clone, Eq)]
struct Endpoint {
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
    fn new(name: &str, address: &str) -> Endpoint {
        Endpoint {
            name: String::from(name),
            address: String::from(address),
            status: RefCell::new(EndpointStatus::Alive),
            last_active_time: RefCell::new(SystemTime::now()),
        }
    }

    fn get_status(&self) -> EndpointStatus {
        self.status.borrow().clone()
    }
}

struct EndpointGroup {
    group: HashMap<String, Endpoint>,
}

impl EndpointGroup {
    fn new() -> EndpointGroup {
        EndpointGroup { group: HashMap::new() }
    }

    fn add_endpoint_to_group(&mut self, endpoint: Endpoint) -> HashSet<Endpoint> {
        let group = &mut self.group;
        let name = &endpoint.name;
        group.insert(name.clone(), endpoint);
        let mut endpoints = HashSet::new();
        group.values().for_each(|mut e| -> () {
            endpoints.insert(e.clone());
        });
        endpoints
    }

    fn remove_endpoint_from_group(&mut self, name: &String) {
        let group = &mut self.group;
        group.remove(name);
    }

    fn get_group(&self) -> HashMap<String, Endpoint> {
        self.group.clone()
    }

    fn update_active_timestamp(&self, name: &String) -> bool {
        match self.group.get(name) {
            None => false,
            Some(endpoint) => {
                *endpoint.last_active_time.borrow_mut() = SystemTime::now();
                true
            }
        }
    }

    fn update_status(&self, name: &String, new_status: EndpointStatus) -> bool {
        match self.group.get(name) {
            None => false,
            Some(endpoint) => {
                *endpoint.status.borrow_mut() = new_status;
                true
            }
        }
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
        let new_group = &group.add_endpoint_to_group(endpoint1.clone());
        assert!(new_group.contains(&endpoint1));
        assert_eq!(1, new_group.len());

        let new_group = &group.add_endpoint_to_group(endpoint2.clone());
        assert!(new_group.contains(&endpoint1));
        assert!(new_group.contains(&endpoint2));
        assert_eq!(2, new_group.len());
    }

    #[test]
    fn add_duplicate_endpoint() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let endpoint2 = Endpoint::new("endpoint1", "127.0.0.1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());
        let new_group = &group.add_endpoint_to_group(endpoint2);
        assert!(new_group.contains(&endpoint1));
        assert_eq!(1, new_group.len());
    }

    #[test]
    fn remove_endpoint() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let endpoint2 = Endpoint::new("endpoint2", "127.0.0.1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());
        group.add_endpoint_to_group(endpoint2.clone());

        group.remove_endpoint_from_group(&endpoint1.name);
        assert!(!group.get_group().contains_key(&endpoint1.name));
        assert_eq!(1, group.get_group().len());

        group.remove_endpoint_from_group(&endpoint2.name);
        assert!(!group.get_group().contains_key(&endpoint2.name));
        assert_eq!(0, group.get_group().len());
    }

    #[test]
    fn update_active_timestamp() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());

        assert!(group.update_active_timestamp(&endpoint1.name));
        assert!(group.get_group().get(&endpoint1.name).unwrap().last_active_time > endpoint1.last_active_time);

        assert!(!group.update_active_timestamp(&String::from("not exists endpoint")));
    }

    #[test]
    fn update_status() {
        let endpoint1 = Endpoint::new("endpoint1", "127.0.0.1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());

        assert!(group.update_status(&endpoint1.name, EndpointStatus::Dead));

        assert_eq!(EndpointStatus::Dead, group.get_group().get(&endpoint1.name).unwrap().get_status());

        assert_ne!(endpoint1.get_status(), group.get_group().get(&endpoint1.name).unwrap().get_status());

        assert!(!group.update_status(&String::from("not exists endpoint"), EndpointStatus::Dead));
    }
}




