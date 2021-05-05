use crate::article::{Article};
use std::error::Error;

mod sqlite_db;

/// This is the arguments used to filter articles from DB
#[derive(Debug)]
pub struct SearchArguments<'a> {
    title: Option<&'a str>,
    limit: u32,
    offset: u32,
}

impl<'a> SearchArguments<'a> {
    pub fn new(title: Option<&'a str>, limit: u32, offset: u32) -> SearchArguments<'a> {
        SearchArguments { title, limit, offset }
    }
}

pub trait BearDb {
    fn search(&self, search_args: &SearchArguments) -> Result<Vec<Article>, Box<dyn Error>>;
}

pub fn get_bear_db() -> Box<dyn BearDb> {
    #[cfg(target_os = "macos")]
        let beardb = sqlite_db::SqliteBearDb {};
    Box::new(beardb)
}