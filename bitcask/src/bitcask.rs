use std::{
    error,
    fs::File,
    io::{Seek, SeekFrom, Write},
    path::Path,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use crc::{Crc, CRC_32_CKSUM};

use crate::keydir::KeyDir;

struct Row {
    crc: u32,
    tstamp: u64,
    key_size: usize,
    value_size: usize,
    key: String,
    value: String,
}

impl Row {
    fn new(key: String, value: String) -> Row {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or(Duration::ZERO)
            .as_millis() as u64;
        let key_size = key.len();
        let value_size = value.len();
        let crc32 = Crc::<u32>::new(&CRC_32_CKSUM);
        let mut ck = crc32.digest();
        ck.update(&now.to_be_bytes());
        ck.update(&key_size.to_be_bytes());
        ck.update(&value_size.to_be_bytes());
        ck.update(key.as_bytes());
        ck.update(value.as_bytes());
        Row {
            crc: ck.finalize(),
            tstamp: now,
            key_size,
            value_size,
            key,
            value,
        }
    }
}

struct Database {
    data_file: File,
    current_offset: usize,
}

impl Database {
    fn open(directory: &Path) -> Result<Database, Box<dyn error::Error>> {
        let data_file = directory.join("data-1");
        let f = File::options().write(true).open(data_file).unwrap();
        Ok(Database {
            data_file: f,
            current_offset: 0,
        })
    }

    pub fn write_row(&mut self, row: Row) -> Result<(), Box<dyn error::Error>> {
        self.data_file.write_all(&row.crc.to_be_bytes()).unwrap();
        self.data_file.write_all(&row.tstamp.to_be_bytes()).unwrap();
        self.data_file
            .write_all(&row.key_size.to_be_bytes())
            .unwrap();
        self.data_file
            .write_all(&row.value_size.to_be_bytes())
            .unwrap();
        self.data_file.write_all(&row.key.as_bytes()).unwrap();
        self.data_file.write_all(&row.value.as_bytes()).unwrap();
        self.data_file.flush().unwrap();
        self.data_file.seek(SeekFrom::Start(0));
        Ok(())
    }
}

struct Bitcask {
    keydir: KeyDir,
    database: Database,
}

impl Bitcask {
    fn open(&mut self, directory: &Path) -> Result<Bitcask, Box<dyn error::Error>> {
        let database = Database::open(directory).unwrap();
        Ok(Bitcask {
            keydir: KeyDir::new(),
            database,
        })
    }
    fn put(&mut self, key: String, value: String) {
        let row = Row::new(key, value);
        self.database.write_row(row);
    }

    fn get(&self, key: String) {}
    fn delete(&self, key: String) {}
    fn close(&self) {}
}
