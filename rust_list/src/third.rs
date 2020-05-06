//! From https://rust-unofficial.github.io/too-many-lists/third-layout.html

use std::rc::Rc;

pub struct List<T> {
    head: Link<T>
}

type Link<T> = Option<Rc<Node<T>>>;

struct Node<T> {
    item: T,
    next: Link<T>,
}

struct Iter<'a, T> {
    next: Option<&'a Rc<Node<T>>>
}

impl<T> List<T> {
    fn new() -> Self {
        List { head: None }
    }

    fn append(&self, val: T) -> List<T> {
        let new_node = Node {
            item: val,
            next: self.head.as_ref().map(|rc_node| {
                Rc::clone(rc_node)
            }),
        };

        List { head: Some(Rc::new(new_node)) }
    }

    fn tail(&self) -> List<T> {
        List {
            head: self.head.as_ref().and_then(|rc_node|
                rc_node.next.clone()
            )
        }
    }

    fn first(&self) -> Option<&T> {
        self.head.as_ref()
            .map(|box_node| {
                &box_node.item
            })
    }

    fn is_empty(&self) -> bool {
        self.head.is_none()
    }

    fn iter(&self) -> Iter<T> {
        Iter { next: self.head.as_ref().map(|boxed_node| boxed_node) }
    }
}

impl<T> Drop for List<T> {
    fn drop(&mut self) {
        let mut cur_link = self.head.take();

        while let Some(node) = cur_link {
            if let Ok(mut node) = Rc::try_unwrap(node) {
                cur_link = node.next.take()
            } else {
                break;
            }
        }
    }
}

impl<'a, T> Iterator for Iter<'a, T> {
    type Item = &'a T;

    fn next(&mut self) -> Option<Self::Item> {
        self.next.map(|node| {
            self.next = node.next.as_ref().map(|node| node);
            &node.item
        })
    }
}

#[cfg(test)]
mod test {
    use super::List;

    #[test]
    fn is_empty() {
        let list: List<u32> = List::new();
        assert!(list.is_empty());
    }

    #[test]
    fn is_not_empty() {
        let list: List<u32> = List::new().append(100);
        assert!(!list.is_empty());
    }

    #[test]
    fn first_on_empty_list() {
        let list: List<u32> = List::new();
        assert_eq!(list.first(), None);
    }

    #[test]
    fn append_val() {
        let list = List::new()
            .append(100)
            .append(200)
            .append(300);

        assert_eq!(list.first(), Some(&300));
    }

    #[test]
    fn append_tail() {
        let list = List::new()
            .append(100)
            .append(200)
            .append(300);

        let list = list.tail();
        assert_eq!(list.first(), Some(&200));

        let list = list.tail();
        assert_eq!(list.first(), Some(&100));

        let list = list.tail();
        assert_eq!(list.first(), None);
    }

    #[test]
    fn iter() {
        let list = List::new()
            .append(100)
            .append(200)
            .append(300);

        let mut iter = list.iter();
        assert_eq!(iter.next(), Some(&300));
        assert_eq!(iter.next(), Some(&200));
        assert_eq!(iter.next(), Some(&100));
        assert_eq!(iter.next(), None);
    }
}
