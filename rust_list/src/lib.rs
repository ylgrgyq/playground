

pub mod first;
pub mod second;
pub mod third;

//#[derive(Debug)]
//pub struct Node {
//    item: u32,
//    next: Option<RefCell<Node>>,
//}
//
//impl Node {
//    pub fn new(item: u32) -> Node {
//        Node { item, next: Option::None }
//    }
//
//    pub fn add_next(&mut self, next: RefCell<Node>) {
//        self.next = Some(RefCell::clone(&next));
//    }
//}

//struct LinkedList {
//    head: Option<RefCell<Node>>,
//    tail: Option<RefCell<Node>>,
//    size: usize,
//}
//
//impl LinkedList {
//    fn new() -> LinkedList {
//        LinkedList { head: None, tail: None, size: 0 }
//    }
//
//    fn add(&mut self, val: u32) -> () {
//        let new_node = RefCell::new(Node::new(val));
//
//        match &self.tail {
//            Some(last) => {
//
//                last.add_next(new_node);
//                self.tail = Some(RefCell::clone(&new_node));
//            }
//            None => {
//                assert!(self.head.is_none());
//                self.tail = Some(RefCell::clone(&new_node));
//                self.head = Some(RefCell::clone(&new_node));
//            }
//        }
//
//        self.size += 1;
//    }
//
//    fn peek(&self) -> Option<&u32> {
//        match &self.head {
//            Some(head) => Some(&head.item),
//            None => None
//        }
//    }
//
//    fn poll(&self) -> Option<u32> {
//        None
//    }
//
//    fn is_empty(&self) -> bool {
//        self.head.is_none()
//    }
//
//    fn clear(&self) -> () {}
//}