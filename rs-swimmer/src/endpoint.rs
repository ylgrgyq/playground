use std::collections::{HashSet, HashMap};
use std::time::{SystemTime, Duration};
use std::cell::RefCell;
use std::collections::hash_map::{RandomState, Entry};
use std::hash::{Hash, Hasher};
use std::borrow::Borrow;
use std::fmt;

#[derive(Debug, Copy, Clone, PartialEq, Eq, Hash)]
pub enum EndpointStatus {
    Alive,
    Suspect,
    Dead,
}

#[derive(Debug, Default, Clone, Hash, PartialEq, Eq, )]
pub struct EndpointId {
    pub name: String,
    pub address: String,
}

impl EndpointId {
    pub fn new(name: &str, address: &str) -> EndpointId {
        EndpointId {
            name: String::from(name),
            address: String::from(address),
        }
    }
}

impl fmt::Display for EndpointId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{{name: {}, address: {}}}", self.name, self.address)
    }
}

#[derive(Debug, Clone, Eq)]
pub struct Endpoint {
    id: EndpointId,
    status: EndpointStatus,
    last_active_time: SystemTime,
}

impl Hash for Endpoint {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.id.name.hash(state);
        self.id.address.hash(state);
    }
}

impl PartialEq for Endpoint {
    fn eq(&self, other: &Self) -> bool {
        return self.id.name == other.id.name;
    }
}

impl Endpoint {
    pub fn new(id: EndpointId, status: EndpointStatus) -> Endpoint {
        Endpoint {
            id,
            status,
            last_active_time: SystemTime::now(),
        }
    }

    pub fn get_status(&self) -> EndpointStatus {
        self.status.borrow().clone()
    }

    pub fn set_status(&mut self, new_status:EndpointStatus) {
        self.status = new_status;
    }

    pub fn get_address(&self) -> &String {
        &self.id.address
    }

    pub fn get_name(&self) -> &String {
        &self.id.name
    }

    pub fn get_id(&self) -> &EndpointId {
        &self.id
    }

    pub fn get_last_active_time(&self) -> SystemTime {
        self.last_active_time
    }

    pub fn get_inactive_duration(&self, now: &SystemTime) -> Duration {
        now.duration_since(self.last_active_time)
            .unwrap_or(Duration::from_secs(0))
    }
}

#[derive(Debug, Default)]
pub struct EndpointGroup {
    group: HashMap<String, Endpoint>,
}

impl EndpointGroup {
    pub fn new() -> EndpointGroup {
        EndpointGroup { group: HashMap::new() }
    }

    pub fn add_endpoint_to_group(&mut self, endpoint: Endpoint) -> bool {
        if self.contains(endpoint.get_name()) {
            return false;
        }

        let group = &mut self.group;
        let name = endpoint.get_name();
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

    pub fn get_endpoints_iter(&self) -> impl Iterator<Item=&Endpoint> {
        self.group.values()
    }

    pub fn get_mut_endpoints_iter(&mut self) -> impl Iterator<Item=&mut Endpoint> {
        self.group.values_mut()
    }

    pub fn clear_dead_endpoints(&mut self) {
        self.group.retain(|_, v| v.status != EndpointStatus::Dead)
    }

    pub fn classify(&self) -> HashMap<EndpointStatus, Vec<&Endpoint>>{
        let mut classified_endpoint: HashMap<EndpointStatus, Vec<&Endpoint>> = HashMap::new();
        for endpoint in self.get_endpoints_iter() {
            let status = endpoint.get_status();
            match classified_endpoint.entry(status) {
                Entry::Occupied(o) => { o.into_mut().push(endpoint); }
                Entry::Vacant(v) => { v.insert(vec![]); }
            }
        }
        classified_endpoint
    }

    pub fn get_endpoint(&self, name: &String) -> Option<&Endpoint> {
        let group = &self.group;
        group.get(name)
    }

    pub fn update_active_timestamp(&mut self, name: &String) -> bool {
        match self.group.get_mut(name) {
            None => false,
            Some(endpoint) => {
                endpoint.last_active_time = SystemTime::now();
                true
            }
        }
    }

    pub fn update_status(&mut self, name: &String, new_status: EndpointStatus) -> bool {
        match self.group.get_mut(name) {
            None => false,
            Some(endpoint) => {
                endpoint.status = new_status;
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
        let endpoint1 = Endpoint::new(EndpointId::new("endpoint1", "127.0.0.1"), EndpointStatus::Alive);
        let endpoint2 = Endpoint::new(EndpointId::new("endpoint2", "127.0.0.1"), EndpointStatus::Alive);
        let mut group = EndpointGroup::new();
        assert!(group.add_endpoint_to_group(endpoint1.clone()));
        assert!(group.contains(endpoint1.get_name()));
        assert_eq!(1, group.len());

        assert!(group.add_endpoint_to_group(endpoint2.clone()));
        assert!(group.contains(endpoint1.get_name()));
        assert!(group.contains(endpoint2.get_name()));
        assert_eq!(2, group.len());
    }

    #[test]
    fn add_duplicate_endpoint() {
        let endpoint1 = Endpoint::new(EndpointId::new("endpoint1", "127.0.0.1"), EndpointStatus::Alive);
        let endpoint2 = Endpoint::new(EndpointId::new("endpoint1", "127.0.0.1"), EndpointStatus::Alive);
        let mut group = EndpointGroup::new();
        assert!(group.add_endpoint_to_group(endpoint1.clone()));
        assert!(!group.add_endpoint_to_group(endpoint2));
        assert!(group.contains(endpoint1.get_name()));
        assert_eq!(1, group.len());
    }

    #[test]
    fn remove_endpoint() {
        let endpoint1 = Endpoint::new(EndpointId::new("endpoint1", "127.0.0.1"), EndpointStatus::Alive);
        let endpoint2 = Endpoint::new(EndpointId::new("endpoint2", "127.0.0.1"), EndpointStatus::Alive);
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());
        group.add_endpoint_to_group(endpoint2.clone());

        assert!(group.remove_endpoint_from_group(endpoint1.get_name()));
        assert!(!group.contains(endpoint1.get_name()));
        assert_eq!(1, group.len());

        assert!(group.remove_endpoint_from_group(endpoint2.get_name()));
        assert!(!group.contains(endpoint2.get_name()));
        assert_eq!(0, group.len());

        assert!(!group.remove_endpoint_from_group(&String::from("not exists endpoint")));
    }

    #[test]
    fn update_active_timestamp() {
        let endpoint1 = Endpoint::new(EndpointId::new("endpoint1", "127.0.0.1"), EndpointStatus::Alive);
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());

        assert!(group.update_active_timestamp(endpoint1.get_name()));
        assert!(group.get_endpoint(endpoint1.get_name()).unwrap().last_active_time > endpoint1.last_active_time);
        assert!(!group.update_active_timestamp(&String::from("not exists endpoint")));
    }

    #[test]
    fn update_status() {
        let endpoint1 = Endpoint::new(EndpointId::new("endpoint1", "127.0.0.1"), EndpointStatus::Alive);
        let mut group = EndpointGroup::new();
        group.add_endpoint_to_group(endpoint1.clone());

        assert!(group.update_status(endpoint1.get_name(), EndpointStatus::Dead));

        assert_eq!(EndpointStatus::Dead, group.get_endpoint(endpoint1.get_name()).unwrap().get_status());

        assert_ne!(endpoint1.get_status(), group.get_endpoint(endpoint1.get_name()).unwrap().get_status());

        assert!(!group.update_status(&String::from("not exists endpoint"), EndpointStatus::Dead));
    }
}




