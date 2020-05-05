//! From https://rust-unofficial.github.io/too-many-lists/first.html

use std::mem;

pub struct List {
    head: Link
}

enum Link {
    Empty,
    More(Box<Node>),
}

struct Node {
    item: u32,
    next: Link,
}

impl List {
    fn new() -> Self {
        List { head: Link::Empty }
    }

    fn push(&mut self, val: u32) {
        let new_node = Node {
            item: val,
            next: mem::replace(&mut self.head, Link::Empty),
        };

        self.head = Link::More(Box::new(new_node));
    }

    fn pop(&mut self) -> Option<u32> {
        match mem::replace(&mut self.head, Link::Empty) {
            Link::More(box_node) => {
                self.head = box_node.next;
                Some(box_node.item)
            }
            Link::Empty => None
        }
    }

    fn is_empty(&self) -> bool {
        match &self.head {
            Link::Empty => true,
            _ => false
        }
    }
}

impl Drop for List {
    fn drop(&mut self) {
        let mut cur_link = mem::replace(&mut self.head, Link::Empty);

        while let Link::More(mut box_node) = cur_link {
            cur_link = mem::replace(&mut box_node.next, Link::Empty)
        }
    }
}


#[cfg(test)]
mod test {
    use super::List;

    #[test]
    fn is_empty() {
        let list = List::new();
        assert!(list.is_empty());
    }

    #[test]
    fn is_not_empty() {
        let mut list = List::new();
        list.push(100);
        assert!(!list.is_empty());
    }

    #[test]
    fn pop_empty() {
        let mut list = List::new();
        assert!(list.pop().is_none());
    }

    #[test]
    fn push_pop_val() {
        let mut list = List::new();
        list.push(100);
        list.push(200);
        list.push(300);

        assert_eq!(list.pop(), Some(300));
        assert_eq!(list.pop(), Some(200));
        assert_eq!(list.pop(), Some(100));
        assert_eq!(list.pop(), None);
    }

    #[test]
    fn intersective_push_pop() {
        let mut list = List::new();
        list.push(100);
        list.push(200);
        list.push(300);

        assert_eq!(list.pop(), Some(300));
        assert_eq!(list.pop(), Some(200));

        list.push(400);
        list.push(500);
        assert_eq!(list.pop(), Some(500));
        assert_eq!(list.pop(), Some(400));
        assert_eq!(list.pop(), Some(100));
        assert_eq!(list.pop(), None);
    }

    #[test]
    fn empty_after_pop_val() {
        let mut list = List::new();
        list.push(100);
        list.pop();
        assert!(list.is_empty());
    }
}
