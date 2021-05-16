use crate::endpoint::{Endpoint, EndpointGroup, EndpointId};
use std::future::{Future};

use std::collections::HashSet;
use std::sync::mpsc::TrySendError::Full;

pub struct Transport {
}

impl Transport {

    pub fn ping(&self, address: &String, current_endpoint: &Endpoint, known_endpoints: HashSet<&Endpoint>)
                -> HashSet<EndpointId> {
        let mut endpoints = HashSet::new();
        endpoints.insert(EndpointId::new("haha", "100"));
        endpoints.insert(EndpointId::new("hoho", "200"));
        endpoints.insert(current_endpoint.get_id().clone());
        endpoints
    }
}
