//! From https://rust-unofficial.github.io/too-many-lists/second.html

pub struct List<T> {
    head: Link<T>
}

type Link<T> = Option<Box<Node<T>>>;

struct Node<T> {
    item: T,
    next: Link<T>,
}

struct IntoIter<T>(List<T>);

struct Iter<'a, T> {
    next: Option<&'a Box<Node<T>>>
}

struct IterMut<'a, T> {
    next: Option<&'a mut Box<Node<T>>>
}

impl<T> List<T> {
    fn new() -> Self {
        List { head: None }
    }

    fn push(&mut self, val: T) {
        let new_node = Node {
            item: val,
            next: self.head.take(),
        };

        self.head = Some(Box::new(new_node));
    }

    fn pop(&mut self) -> Option<T> {
        self.head.take()
            .map(|box_node| {
                self.head = box_node.next;
                box_node.item
            })
    }

    fn peek(&self) -> Option<&T> {
        self.head.as_ref()
            .map(|box_node| {
                &box_node.item
            })
    }

    fn peek_mut(&mut self) -> Option<&mut T> {
        self.head.as_mut()
            .map(|box_node| {
                &mut box_node.item
            })
    }

    fn is_empty(&self) -> bool {
        self.head.is_none()
    }

    fn into_iter(self) -> IntoIter<T> {
        IntoIter(self)
    }

    fn iter(&self) -> Iter<T> {
        Iter { next: self.head.as_ref().map(|boxed_node| boxed_node) }
    }

    fn iter_mut(&mut self) -> IterMut<T> {
        IterMut { next: self.head.as_mut().map(|boxed_node| boxed_node) }
    }
}

impl<T> Drop for List<T> {
    fn drop(&mut self) {
        let mut cur_link = self.head.take();

        while let Some(mut box_node) = cur_link {
            cur_link = box_node.next.take()
        }
    }
}

impl<T> Iterator for IntoIter<T> {
    type Item = T;

    fn next(&mut self) -> Option<Self::Item> {
        self.0.pop()
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

impl<'a, T> Iterator for IterMut<'a, T> {
    type Item = &'a mut T;

    fn next(&mut self) -> Option<Self::Item> {
        self.next.take().map(|node| {
            self.next = node.next.as_mut().map(|node| node);
            &mut node.item
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
        let mut list: List<u32> = List::new();
        list.push(100);
        assert!(!list.is_empty());
    }

    #[test]
    fn pop_empty() {
        let mut list: List<u32> = List::new();
        assert!(list.pop().is_none());
    }

    #[test]
    fn push_pop_val() {
        let mut list: List<u32> = List::new();
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
        let mut list: List<u32> = List::new();
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
        let mut list: List<u32> = List::new();
        list.push(100);
        list.pop();
        assert!(list.is_empty());
    }

    #[test]
    fn peek() {
        let mut list: List<&str> = List::new();
        list.push("haha");
        list.push("hoho");

        assert_eq!(list.peek(), Some(&"hoho"));
    }

    #[test]
    fn peek_mut() {
        let mut list: List<u32> = List::new();
        list.push(100);
        list.push(200);

        assert_eq!(list.peek_mut(), Some(&mut 200));
        assert_eq!(list.peek_mut()
                       .map(|v| {
                           *v = 1;
                           v
                       }),
                   Some(&mut 1));
    }

    #[test]
    fn peek_empty_list() {
        let mut list: List<&str> = List::new();
        assert_eq!(list.peek(), None);
        assert_eq!(list.peek_mut(), None);
    }

    #[test]
    fn into_iter() {
        let mut list: List<u32> = List::new();
        list.push(100);
        list.push(200);
        list.push(300);

        let mut iter = list.into_iter();
        assert_eq!(iter.next(), Some(300));
        assert_eq!(iter.next(), Some(200));
        assert_eq!(iter.next(), Some(100));
        assert_eq!(iter.next(), None);
    }

    #[test]
    fn iter() {
        let mut list: List<u32> = List::new();
        list.push(100);
        list.push(200);
        list.push(300);

        let mut iter = list.iter();
        assert_eq!(iter.next(), Some(&300));
        assert_eq!(iter.next(), Some(&200));
        assert_eq!(iter.next(), Some(&100));
        assert_eq!(iter.next(), None);
    }

    #[test]
    fn iter_mut() {
        let mut list: List<u32> = List::new();
        list.push(100);
        list.push(200);
        list.push(300);

        let mut iter = list.iter_mut();

        iter.next().map(|v| {
            *v = *v + 1;
            *v
        });
        iter.next().map(|v| {
            *v = *v + 1;
            *v
        });
        iter.next().map(|v| {
            *v = *v + 1;
            *v
        });

        let mut iter = list.iter();
        assert_eq!(iter.next(), Some(&301));
        assert_eq!(iter.next(), Some(&201));
        assert_eq!(iter.next(), Some(&101));
        assert_eq!(iter.next(), None);
    }
}
