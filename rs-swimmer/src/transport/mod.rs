mod http_transport;

use crate::endpoint::{Endpoint};
use std::collections::HashSet;

trait PingResponseHandler {
    fn handle_ping_response(&mut self, from_endpoint: &Endpoint, known_endpoints: HashSet<&Endpoint>);
}

trait PingReqHandler {
    fn handle_ping_req(&mut self, from_endpoint: &Endpoint, known_endpoints: HashSet<&Endpoint>);
}

pub trait Transport {
    fn ping(&self, from: &String, target_address: &String, known_endpoints: HashSet<Endpoint>);

    fn ping_req(&self, origin: &String, target: &String);

    fn left(&self, from: &String);

    fn suspect(&self, from: &String);

    fn add_ping_response_handler<H: PingResponseHandler>(&mut self, handler: H);
}

struct MockTransport {}

impl Transport for MockTransport {
    fn ping(&self, from_name: &String, target_address: &String, known_endpoints: HashSet<Endpoint>) {
        let mut endpoints = HashSet::new();
        endpoints.insert(from_name.clone());
    }

    fn ping_req(&self, origin: &String, target: &String) {
        todo!()
    }

    fn left(&self, from: &String) {
        todo!()
    }

    fn suspect(&self, from: &String) {
        todo!()
    }

    fn add_ping_response_handler<H: PingResponseHandler>(&mut self, handler: H) {
        todo!()
    }
}