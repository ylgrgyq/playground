use std::collections::HashMap;

struct Index {
    file_id: String,
    value_size: usize,
    value_pos: usize,
    tstamp: u64,
}

pub struct KeyDir {
    index: HashMap<String, Index>,
}

impl KeyDir {
    fn put(&mut self, key: String, value: Index) {
        self.index.insert(key, value);
    }

    fn get(&self, key: &String) -> Option<&Index> {
        self.index.get(key)
    }

    fn delete(&mut self, key: &String) -> Option<Index> {
        self.index.remove(key)
    }
}
