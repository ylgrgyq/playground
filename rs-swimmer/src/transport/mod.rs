mod http_transport;

use crate::endpoint::{EndpointId, Endpoint};
use std::collections::HashSet;

trait PingResponseHandler {
    fn handle_ping_response(&mut self, from_endpoint: &Endpoint, known_endpoints: HashSet<&Endpoint>);
}

trait PingReqHandler {
    fn handle_ping_req(&mut self, from_endpoint: &Endpoint, known_endpoints: HashSet<&Endpoint>);
}

pub trait Transport {
    fn ping(&self, target_address: &String, from: &EndpointId, known_endpoints: HashSet<&Endpoint>);

    fn ping_req(&self, target_address: &String, origin: &EndpointId, target: &EndpointId);

    fn left(&self, from: &EndpointId);

    fn suspect(&self, from: &EndpointId);

    fn add_ping_response_handler<H: PingResponseHandler>(&mut self, handler: H);
}

struct MockTransport {

}

impl Transport for MockTransport {
    fn ping(&self, target_address: &String, from: &EndpointId, known_endpoints: HashSet<&Endpoint>) {
        let mut endpoints = HashSet::new();
        endpoints.insert(EndpointId::new("haha", "100"));
        endpoints.insert(EndpointId::new("hoho", "200"));
        endpoints.insert(from.clone());
    }

    fn ping_req(&self, target_address: &String, origin: &EndpointId, target: &EndpointId) {
        todo!()
    }

    fn left(&self, from: &EndpointId) {
        todo!()
    }

    fn suspect(&self, from: &EndpointId) {
        todo!()
    }

    fn add_ping_response_handler<H: PingResponseHandler>(&mut self, handler: H) {
        todo!()
    }
}