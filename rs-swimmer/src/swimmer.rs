use crate::endpoint::{Endpoint, EndpointGroup, EndpointStatus, EndpointWithState};
use crate::transport::{Transport};
use std::time::{SystemTime, Duration, Instant};
use std::collections::{HashSet, HashMap};
use std::option::{Iter, IntoIter};
use std::sync::{Arc, RwLock, Mutex};
use std::thread::JoinHandle;
use std::cell::Cell;
use std::sync::mpsc;
use std::sync::mpsc::RecvTimeoutError;
use futures::SinkExt;
use core::mem;
use std::net::Shutdown::Read;
use std::collections::hash_map::Entry;

trait EndpointChangeListener {
    fn on_endpoint_changed(&self, name: &String, status: &EndpointStatus);
}


// struct Swimmer<L: EndpointChangeListener, T: Transport> {
//     stopped: bool,
//     endpoint_change_listeners: Arc<RwLock<Vec<L>>>,
//     transport: Arc<T>,
//     core_thread: JoinHandle<()>,
//     sender: mpsc::Sender<InputCommand>,
// }
//
// impl<L: EndpointChangeListener + Send + Sync + 'static, T: Transport + Send + Sync + 'static> Swimmer<L, T> {
//     pub fn new(name: &str, address: &str, transport: T) -> Swimmer<L, T> {
//         let endpoint_change_listeners = Arc::new(RwLock::new(vec![]));
//         let transport = Arc::new(transport);
//         let (sender, receiver) = mpsc::channel();
//         let core_thread = Swimmer::start_state_maintainer(
//             String::from(name),
//             String::from(address),
//             endpoint_change_listeners.clone(),
//             transport.clone(),
//             Duration::from_secs(10),
//             receiver,
//         );
//         Swimmer {
//             stopped: false,
//             endpoint_change_listeners,
//             core_thread,
//             transport,
//             sender,
//         }
//     }
//
//     pub fn join(&mut self, name: String, address: String) -> Result<HashSet<&Endpoint>, String> {
//         if self.stopped {
//             return Err(format!("Swimmer has stopped"));
//         }
//
//         self.sender.send(InputCommand::Join(name, address));
//         Ok(self.get_endpoints()?)
//     }
//
//     // pub fn batch_join<I>(&mut self, endpoint_ids: I) -> Result<HashSet<&Endpoint>, String>
//     // where I: IntoIterator<Item=EndpointId> {
//     //     if self.stopped {
//     //         return Err(format!("Swimmer has stopped"));
//     //     }
//     //
//     //     for id in endpoint_ids {
//     //         self.sender.send(InputCommand::Join(id));
//     //     }
//     //     Ok(self.get_endpoints()?)
//     // }
//
//     pub fn get_endpoints(&self) -> Result<HashSet<&Endpoint>, String> {
//         if self.stopped {
//             return Err(format!("Swimmer has stopped"));
//         }
//
//         // Ok(self.state.get_endpoints())
//         Err(String::from("asdf"))
//     }
//
//     pub fn add_endpoint_change_listener(&mut self, listener: L) -> Result<(), String> {
//         if self.stopped {
//             return Err(format!("Swimmer has stopped"));
//         }
//
//         self.endpoint_change_listeners.write().unwrap().push(listener);
//         Ok(())
//     }
//
//     fn start_state_maintainer(name: String,
//                               address: String,
//                               listeners: Arc<RwLock<Vec<L>>>,
//                               transport: Arc<T>,
//                               ping_interval: Duration,
//                               receiver: mpsc::Receiver<InputCommand>)
//                               -> JoinHandle<()> {
//         let mut state = SwimmerStateMaintainer::new(name.clone(), address);
//         let handle = std::thread::spawn(move || {
//             let on_ready = |state: &mut SwimmerStateMaintainer| {
//                 let mut ready = state.ready();
//
//                 for event in ready.take_event_box() {
//                     match event {
//                         OutputEvent::Ping(name, addr, ends) => {
//                             transport.ping(&name, &addr, ends)
//                         }
//                         OutputEvent::PingReq(addr) => {
//                             // transport.ping_req(&self_id, &addr, ends)
//                         }
//                     }
//                 }
//
//                 for endpoint in ready.take_changed_endpoints() {
//                     for l in listeners.read().unwrap().iter() {
//                         // l.on_endpoint_changed(&endpoint.get_id(), &endpoint.get_status())
//                     }
//                 }
//             };
//
//             let mut t = Instant::now();
//             let mut timeout = ping_interval;
//             loop {
//                 match receiver.recv_timeout(timeout) {
//                     Ok(InputCommand::Join(name, address)) => {
//                         state.join(name, address)
//                     }
//                     Err(RecvTimeoutError::Timeout) => (),
//                     Err(RecvTimeoutError::Disconnected) => {
//                         state.shutdown();
//                         return;
//                     }
//                 }
//
//                 let d = t.elapsed();
//                 t = Instant::now();
//                 if d >= timeout {
//                     timeout = ping_interval;
//                     state.tick();
//                 } else {
//                     timeout -= d;
//                 }
//
//                 on_ready(&mut state);
//             }
//         });
//         handle
//     }
// }
//
// #[cfg(test)]
// mod tests {
//     use super::*;
//
//     #[test]
//     fn basic_add_endpoint() {
//
//     }
// }