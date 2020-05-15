//! From https://rust-unofficial.github.io/too-many-lists/fifth.html

use std::ptr;

type Link<T> = Option<Box<Node<T>>>;

struct List<T> {
    head: Link<T>,
    tail: *mut Node<T>,
}

struct Node<T> {
    item: T,
    next: Link<T>,
}

struct IntoIter<T> (List<T>);

struct Iter<'a, T> {
    next: Option<&'a Box<Node<T>>>
}

struct IterMut<'a, T> {
    next: Option<&'a mut Box<Node<T>>>
}

impl<T> List<T> {
    fn new() -> List<T> {
        List { head: None, tail: ptr::null_mut() }
    }

    fn push(&mut self, item: T) {
        let mut new_node = Box::new(Node {
            item,
            next: None,
        });

        // raw_ptr 类型为 *mut Node<T>
        // *new_node 是因为 new_node 是 Box<Node<T>>，加个 * 才能变成 Node<T>
        // &mut 可以赋值给 &mut 也可以赋值给 *mut，参考：https://stackoverflow.com/questions/31949579/understanding-and-relationship-between-box-ref-and/31953048#31953048
        let raw_ptr: *mut _ = &mut *new_node;

        if self.tail.is_null() {
            self.head = Some(new_node);
        } else {
            unsafe {
                (*self.tail).next = Some(new_node);
            }
        }

        self.tail = raw_ptr;
    }

    fn pop(&mut self) -> Option<T> {
        self.head.take().map(|head| {
            let head = *head;

            self.head = head.next;

            if self.head.is_none() {
                self.tail = ptr::null_mut();
            }

            head.item
        })
    }

    fn peek(&self) -> Option<&T> {
        self.head.as_ref().map(|head| {
            &head.item
        })
    }

    fn peek_mut(&mut self) -> Option<&mut T> {
        self.head.as_mut().map(|head| {
            &mut head.item
        })
    }

    fn into_iter(self) -> IntoIter<T> {
        IntoIter(self)
    }

    fn iter(&self) -> Iter<T> {
        Iter {
            next: self.head.as_ref().map(|head| {
                head
            })
        }
    }

    fn iter_mut(&mut self) -> IterMut<T> {
        IterMut {
            next: self.head.as_mut().map(|head| {
                head
            })
        }
    }
}

impl<T> Drop for List<T> {
    fn drop(&mut self) {
        let mut cur_link = self.head.take();
        while let Some(mut node) = cur_link {
            cur_link = node.next.take();
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
        // self.next.map(...) 也行，相当于 Copy 了一份 self.next 传给 map，因为 self.next 是 immutable reference
        // 所以 Copy 可行，下面的 mutable reference 就不能 Copy 了，必须明确的用 take
        self.next.take().map(|boxed_node| {
            self.next = boxed_node.next.as_ref().map(|next_node| next_node);
            &boxed_node.item
        })
    }
}

impl<'a, T> Iterator for IterMut<'a, T> {
    type Item = &'a mut T;

    fn next(&mut self) -> Option<Self::Item> {
        self.next.take().map(|boxed_node| {
            self.next = boxed_node.next.as_mut().map(|next_node| next_node);
            &mut boxed_node.item
        })
    }
}

#[cfg(test)]
mod test {
    use super::List;

    #[test]
    fn pop_from_empty_list() {
        let mut list: List<u32> = List::new();
        assert_eq!(list.pop(), None);
    }

    #[test]
    fn push_pop() {
        let mut list = List::new();
        list.push(1);
        list.push(2);
        list.push(3);

        assert_eq!(list.pop(), Some(1));
        assert_eq!(list.pop(), Some(2));
        assert_eq!(list.pop(), Some(3));
        assert_eq!(list.pop(), None);
    }

    #[test]
    fn peek() {
        let mut list = List::new();
        assert_eq!(list.peek(), None);

        list.push(1);
        assert_eq!(list.peek(), Some(&1));

        list.push(2);
        assert_eq!(list.peek(), Some(&1));
    }

    #[test]
    fn peek_mut() {
        let mut list = List::new();
        assert_eq!(list.peek_mut(), None);

        list.push(1);
        list.peek_mut().map(|v| *v = 22);

        assert_eq!(list.peek(), Some(&22));
    }

    #[test]
    fn into_iter() {
        let mut list: List<u32> = List::new();
        list.push(100);
        list.push(200);
        list.push(300);

        let mut iter = list.into_iter();
        assert_eq!(iter.next(), Some(100));
        assert_eq!(iter.next(), Some(200));
        assert_eq!(iter.next(), Some(300));
        assert_eq!(iter.next(), None);
    }

    #[test]
    fn iter() {
        let mut list: List<u32> = List::new();
        list.push(100);
        list.push(200);
        list.push(300);

        let mut iter = list.iter();
        assert_eq!(iter.next(), Some(&100));
        assert_eq!(iter.next(), Some(&200));
        assert_eq!(iter.next(), Some(&300));
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
        assert_eq!(iter.next(), Some(&101));
        assert_eq!(iter.next(), Some(&201));
        assert_eq!(iter.next(), Some(&301));
        assert_eq!(iter.next(), None);
    }
}
