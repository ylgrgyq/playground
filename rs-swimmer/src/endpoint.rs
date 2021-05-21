use std::collections::{HashSet, HashMap};
use std::time::{SystemTime, Duration};
use std::cell::RefCell;
use std::collections::hash_map::{RandomState, Entry};
use std::hash::{Hash, Hasher};
use std::borrow::Borrow;
use std::fmt;
use futures::FutureExt;

#[derive(Debug, Copy, Clone, PartialEq, Eq, Hash)]
pub enum EndpointStatus {
    Alive,
    Suspect,
    Dead,
}

#[derive(Debug, Clone, Eq)]
pub struct Endpoint {
    name: String,
    address: String,
}

impl Hash for Endpoint {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.name.hash(state);
    }
}

impl PartialEq for Endpoint {
    fn eq(&self, other: &Self) -> bool {
        return self.name == other.name;
    }
}

impl Endpoint {
    pub fn new(name: String, address: String) -> Endpoint {
        Endpoint {
            name,
            address,
        }
    }

    pub fn get_address(&self) -> &String {
        &self.address
    }

    pub fn get_name(&self) -> &String {
        &self.name
    }
}

#[derive(Debug, Clone, Eq)]
pub struct EndpointWithState {
    endpoint: Endpoint,
    incarnation: u32,
    status: EndpointStatus,
    last_state_change_time: SystemTime,
}

impl Hash for EndpointWithState {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.endpoint.name.hash(state);
    }
}

impl PartialEq for EndpointWithState {
    fn eq(&self, other: &Self) -> bool {
        return self.endpoint.name == other.endpoint.name;
    }
}

impl EndpointWithState {
    pub fn new(endpoint: Endpoint, incarnation: u32, status: EndpointStatus) -> EndpointWithState {
        EndpointWithState {
            endpoint,
            incarnation,
            status,
            last_state_change_time: SystemTime::now(),
        }
    }

    pub fn get_endpoint(&self) -> &Endpoint {
        &self.endpoint
    }

    pub fn clone_endpoint(&self) -> Endpoint {
        self.endpoint.clone()
    }

    pub fn get_incarnation(&self) -> u32 {
        self.incarnation
    }

    pub fn get_status(&self) -> EndpointStatus {
        self.status
    }

    pub fn set_status(&mut self, new_status: EndpointStatus) {
        self.status = new_status;
    }

    pub fn get_last_state_change_time(&self) -> SystemTime {
        self.last_state_change_time
    }
}


#[derive(Debug, Default)]
pub struct EndpointGroup {
    group: HashMap<String, EndpointWithState>,
}

impl EndpointGroup {
    pub fn new() -> EndpointGroup {
        EndpointGroup { group: HashMap::new() }
    }

    pub fn add_endpoint_to_group(&mut self, endpoint: EndpointWithState) -> bool {
        if self.contains(endpoint.get_endpoint().get_name()) {
            return false;
        }

        let group = &mut self.group;
        let name = endpoint.get_endpoint().get_address();
        group.insert(name.clone(), endpoint);
        true
    }

    pub fn remove_endpoint_from_group(&mut self, name: &String) -> Option<EndpointWithState> {
        let group = &mut self.group;
        group.remove(name)
    }

    pub fn contains(&self, name: &String) -> bool {
        self.group.contains_key(name)
    }

    pub fn get_endpoints_iter(&self) -> impl Iterator<Item=&EndpointWithState> {
        self.group.values()
    }

    pub fn get_mut_endpoints_iter(&mut self) -> impl Iterator<Item=&mut EndpointWithState> {
        self.group.values_mut()
    }

    pub fn classify_endpoints_by_status(&self) -> HashMap<EndpointStatus, Vec<String>> {
        let mut classified_endpoint: HashMap<EndpointStatus, Vec<String>> = HashMap::new();
        for endpoint in self.get_endpoints_iter() {
            let status = endpoint.get_status();
            match classified_endpoint.entry(status) {
                Entry::Occupied(o) => { o.into_mut().push(endpoint.get_endpoint().get_name().clone()); }
                Entry::Vacant(v) => { v.insert(vec![]); }
            }
        }
        classified_endpoint
    }

    pub fn get_endpoint(&self, name: &String) -> Option<&EndpointWithState> {
        let group = &self.group;
        group.get(name)
    }

    pub fn update_status(&mut self, name: &String, new_status: EndpointStatus) -> bool {
        match self.group.get_mut(name) {
            None => false,
            Some(endpoint) => {
                endpoint.status = new_status;
                endpoint.last_state_change_time = SystemTime::now();
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
        let endpoint1 = create_test_endpoint_with_state("endpoint1");
        let endpoint2 = create_test_endpoint_with_state("endpoint2");
        let mut group = EndpointGroup::new();
        assert!(group.add_endpoint_to_group(endpoint1.clone()));
        assert!(group.contains(endpoint1.get_endpoint().get_name()));
        assert_eq!(1, group.len());

        assert!(group.add_endpoint_to_group(endpoint2.clone()));
        assert!(group.contains(endpoint1.get_endpoint().get_name()));
        assert!(group.contains(endpoint2.get_endpoint().get_name()));
        assert_eq!(2, group.len());
    }

    #[test]
    fn add_duplicate_endpoint() {
        let endpoint1 = create_test_endpoint_with_state("endpoint1");
        let endpoint2 = create_test_endpoint_with_state("endpoint1");
        let mut group = EndpointGroup::new();
        assert!(group.add_endpoint_to_group(endpoint1.clone()));
        assert!(!group.add_endpoint_to_group(endpoint2));
        assert!(group.contains(endpoint1.get_endpoint().get_name()));
        assert_eq!(1, group.len());
    }

    #[test]
    fn remove_endpoint() {
        let endpoint1 = create_test_endpoint_with_state("endpoint1");
        let endpoint2 = create_test_endpoint_with_state("endpoint2");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());
        group.add_endpoint_to_group(endpoint2.clone());

        assert_eq!(endpoint1, group.remove_endpoint_from_group(endpoint1.get_endpoint().get_name()).unwrap());
        assert!(!group.contains(endpoint1.get_endpoint().get_name()));
        assert_eq!(1, group.len());

        assert_eq!(endpoint2, group.remove_endpoint_from_group(endpoint2.get_endpoint().get_name()).unwrap());
        assert!(!group.contains(endpoint2.get_endpoint().get_name()));
        assert_eq!(0, group.len());

        assert_eq!(None, group.remove_endpoint_from_group(&String::from("not exists endpoint")));
    }

    #[test]
    fn update_status() {
        let endpoint1 = create_test_endpoint_with_state("endpoint1");
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());

        assert!(group.update_status(endpoint1.get_endpoint().get_name(), EndpointStatus::Dead));

        assert_eq!(EndpointStatus::Dead, group.get_endpoint(endpoint1.get_endpoint().get_name()).unwrap().get_status());

        assert_ne!(endpoint1.get_status(), group.get_endpoint(endpoint1.get_endpoint().get_name()).unwrap().get_status());

        assert!(!group.update_status(&String::from("not exists endpoint"), EndpointStatus::Dead));
    }

    fn create_test_endpoint(name: &str) -> Endpoint {
        Endpoint {
            name: String::from(name),
            address: String::from("127.0.0.1"),
        }
    }

    fn create_test_endpoint_with_state(name: &str) -> EndpointWithState {
        let endpoint = create_test_endpoint(name);
        EndpointWithState {
            endpoint,
            incarnation: 100,
            status: EndpointStatus::Alive,
            last_state_change_time: SystemTime::now(),
        }
    }
}




