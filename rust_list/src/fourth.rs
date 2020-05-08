use std::cell::{RefCell, Ref};
use std::rc::Rc;

/// 一个 Node 可能会被前后 Node 指向，还可能被 head 或 tail 指向，所以只有一个 owner 不行，得用 Rc
/// 有了 Rc 之后一个对象能有多个 owner。但是每个 owner 只能拿到对象的不可变引用，不能做修改，但我们又
/// 需要修改 Node 的 prev，next 指针，也就是说需要用一个不可变引用修改被引用的对象，所以需要用 RefCell
type Link<T> = Option<Rc<RefCell<Node<T>>>>;

struct List<T> {
    head: Link<T>,
    tail: Link<T>,
}

#[derive(Debug)]
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
                // Rc 是引用，这里会自动的 dereference cohesion 相当于 (*old_node).borrow_mut()
                // 这里看到有两个 borrow_mut() 但是 borrow 的不同 node，所以没事，如果 borrow_mut 同一个 node
                // 的同一个 Link 就会在 Runtime 时候报错
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

    fn peek_front(&self) -> Option<Ref<T>> {
        self.head.as_ref().map(|head| {
            let ref_node = head.borrow();
            Ref::map(ref_node, |node| {
                &node.item
            })
        })
    }

//    fn peek_front2(&self) -> Option<&T> {
//        self.head.as_ref().map(|head| {
//            // 这么写是不行的，因为 borrow 得到的 Ref 生命周期只能在当前这个括号内
//            // 从这个 ref_cell 得到的 item 生命周期和 Ref 生命周期一样，也只在这个括号内
//            // 所以 &ref_cell.item 得到的生命周期也是在这个括号内，就不能作为返回值返回出去了
//            // 所以得用 peek_front() 里的方法，将 Ref 直接作为返回值 move 到返回结果里
//            let ref_cell: Ref<Node<T>> = head.borrow();
//            &ref_cell.item
//        })
//    }

//    fn peek_front_node(&self) -> Option<Ref<Node<T>>> {
//        self.head.as_ref().map(|head| {
//            // 这种写法也行，也是将 borrow() 生成的 Ref 对象作为返回值 move 出去，但是让外界能看到 Node 细节
//            // 破坏了封装
//            head.borrow()
//        })
//    }
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

    #[test]
    fn peek_front_on_empty_list() {
        let list: List<u32> = List::new();
        assert!(list.peek_front().is_none());
    }

    #[test]
    fn peek_front() {
        let mut list: List<u32> = List::new();
        list.push_front(100);
        assert_eq!(&*list.peek_front().unwrap(), &100);
        list.push_front(200);
        assert_eq!(&*list.peek_front().unwrap(), &200);
        list.push_front(300);
        assert_eq!(&*list.peek_front().unwrap(), &300);
    }
}