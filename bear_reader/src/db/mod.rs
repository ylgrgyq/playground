use crate::note::{Note};
use crate::{SearchArguments};
use std::error::Error;

mod sqlite_db;

pub trait BearDb {
    fn search(&self, search_args: &SearchArguments) -> Result<Vec<Note>, Box<dyn Error>>;
}

pub fn get_bear_db() -> Box<dyn BearDb> {
    #[cfg(target_os = "macos")]
        let beardb = sqlite_db::SqliteBearDb {};
    Box::new(beardb)
}