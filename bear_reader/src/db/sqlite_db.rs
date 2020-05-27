use rusqlite::{Connection, params, ToSql};
use std::error::Error;
use crate::{SearchArguments};
use crate::note::{Note};
use crate::db::{BearDb};

const DEFAULT_BEAR_SQLITE_DB_PATH: &str = "Library/Group Containers/9K33E3U3T4.net.shinyfrog.bear/Application Data/database.sqlite";

pub struct SqliteBearDb {}

fn get_db_path() -> String {
    let home = env!("HOME");
    let bear_db_path = DEFAULT_BEAR_SQLITE_DB_PATH;

    format!("{}/{}", home, bear_db_path)
}

fn get_db_connection() -> rusqlite::Result<Connection> {
    let db_path = get_db_path();
    Connection::open(db_path)
}

fn do_search(sql: &str, args: &[&dyn ToSql]) -> Result<Vec<Note>, Box<dyn Error>> {
    let mut ret: Vec<Note> = vec![];
    let conn = get_db_connection()?;
    let mut stmt = conn.prepare(sql)?;

    stmt.query_map(
        args,
        |row| {
            let uuid = row.get(0)?;
            let title = row.get(1)?;
            let text = row.get(2)?;
            Ok(Note::new(uuid, title, text))
        })?
        .for_each(|note| {
            if note.is_ok() {
                ret.push(note.unwrap())
            }
        });
    Ok(ret)
}

const SEARCH_WITH_TITLE: &str = "SELECT ZUNIQUEIDENTIFIER, ZTITLE, ZTEXT FROM `ZSFNOTE` \
         WHERE `ZTRASHED` LIKE '0' AND `ZARCHIVED` LIKE '0' AND `ZTITLE` == ?1 LIMIT ?2,?3";
const SEARCH_WITHOUT_TITLE: &str = "SELECT ZUNIQUEIDENTIFIER, ZTITLE, ZTEXT FROM `ZSFNOTE` \
        WHERE `ZTRASHED` LIKE '0' AND `ZARCHIVED` LIKE '0' LIMIT ?1,?2";

impl BearDb for SqliteBearDb {
    fn search(&self, search_args: &SearchArguments) -> Result<Vec<Note>, Box<dyn Error>> {
        if search_args.title.is_some() {
            do_search(SEARCH_WITH_TITLE,
                      params![search_args.title.unwrap(), search_args.offset, search_args.limit])
        } else {
            do_search(SEARCH_WITHOUT_TITLE,
                      params![search_args.offset, search_args.limit])
        }
    }
}


