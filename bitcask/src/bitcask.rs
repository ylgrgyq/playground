use std::{
    error,
    fs::File,
    io::Write,
    path::Path,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use crate::keydir::KeyDir;

struct Bitcask {
    keydir: KeyDir,
    data_file: File,
}

impl Bitcask {
    fn open(&mut self, directory: &Path) -> Result<Bitcask, Box<dyn error::Error>> {
        let data_file = directory.join("data-1");
        let f = File::options().write(true).open(data_file).unwrap();
        self.data_file = f;
        Err("".into())
    }
    fn put(&self, key: String, value: String) {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or(Duration::ZERO)
            .as_millis() as u64;
        // now.self.data_file.write(buf)
    }
    fn get(&self, key: String) {}
    fn delete(&self, key: String) {}
    fn close(&self) {}
}
