use crate::endpoint::{Endpoint, EndpointGroup};
use std::future::{Future};

use std::collections::HashSet;
use std::sync::mpsc::TrySendError::Full;

pub struct Transport {
}

impl Transport {

    pub fn ping(&self, address: String, current_endpoint: &Endpoint, known_endpoints: HashSet<&Endpoint>)
            -> impl Future<Output=HashSet<Endpoint>> {
        let mut endpoints = HashSet::new();
        endpoints.insert(Endpoint::new("haha", "100"));
        endpoints.insert(Endpoint::new("hoho", "200"));
        endpoints.insert(current_endpoint.clone());
        std::future::ready(endpoints)
    }


}
