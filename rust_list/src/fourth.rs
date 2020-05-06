use std::cell::RefCell;
use std::rc::Rc;

/// 一个 Node 可能会被前后 Node 指向，还可能被 head 或 tail 指向，所以只有一个 owner 不行，得用 Rc
/// 有了 Rc 之后
///
type Link<T> = Option<Rc<RefCell<Node<T>>>>;

struct List<T> {
    head: Link<T>,
    tail: Link<T>,
}

struct Node<T> {
    item: T,
    prev: Link<T>,
    next: Link<T>,
}

impl<T> Node<T> {
    fn new(item: T) -> Node<T> {
        Node { item, prev: None, next: None }
    }
}

impl<T> List<T> {
    fn new() -> List<T> {
        List { head: None, tail: None }
    }

    fn push_front(&mut self, item: T) {
        let new_node = Rc::new(RefCell::new(Node::new(item)));

        match self.head.take() {
            Some(old_node) => {
                old_node.borrow_mut().prev = Some(Rc::clone(&new_node));
                new_node.borrow_mut().next = Some(old_node);
                self.head = Some(new_node);
            }
            None => {
                self.tail = Some(Rc::clone(&new_node));
                self.head = Some(new_node)
            }
        }
    }

    fn pop_front(&mut self) -> Option<T> {
        self.head.take().map(|old_head| {
            match old_head.borrow_mut().next.take() {
                Some(new_head) => {
                    new_head.borrow_mut().prev.take();
                    self.head = Some(new_head)
                }
                None => {
                    self.tail.take();
                }
            }
            Rc::try_unwrap(old_head).ok().unwrap().into_inner().item
        })
    }
}

impl<T> Drop for List<T> {
    fn drop(&mut self) {
        while self.pop_front().is_some() {}
    }
}

#[cfg(test)]
mod test {
    use super::List;

    #[test]
    fn push_pop_front() {
        let mut list = List::new();
        list.push_front(100);
        list.push_front(200);
        list.push_front(300);

        assert_eq!(list.pop_front(), Some(300));
        assert_eq!(list.pop_front(), Some(200));
        assert_eq!(list.pop_front(), Some(100));
    }
}