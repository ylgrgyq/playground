use rusqlite::{Connection, NO_PARAMS, params, ToSql};
use std::error::Error;
use crate::{SearchArguments, BearDb};
use crate::note::{Note};

pub struct SqliteBearDb {}

fn get_db_path() -> String {
    let home = env!("HOME");
    let bear_db_path = "Library/Group Containers/9K33E3U3T4.net.shinyfrog.bear/Application Data/database.sqlite";

    format!("{}/{}", home, bear_db_path)
}

fn get_db_connection() -> rusqlite::Result<Connection> {
    let db_path = get_db_path();
    Connection::open(db_path)
}

fn generate_sql(search_args: &SearchArguments) -> String {
    let title_filter = search_args.title.as_ref()
        .map(|title| format!(" AND `ZTITLE` == '{}'", title))
        .unwrap_or(String::from(""));

    format!("SELECT ZTITLE, ZTEXT FROM `ZSFNOTE` \
        WHERE `ZTRASHED` LIKE '0' AND `ZARCHIVED` LIKE '0' {} LIMIT ?1,?2",
            title_filter)
}

struct Sql<'a, T: ?Sized> {
    sql: &'a str,
    args: T,
}

fn p<'a>(search_args: &'a SearchArguments) -> Sql<'a, [&'a dyn ToSql; 2]> {
// let a : [&dyn ToSql; 2] = [&search_args.offset, &search_args.limit];
    Sql { sql: "asdfsdf", args: [&search_args.offset, &search_args.limit] }
}

impl BearDb for SqliteBearDb {
    fn search(&self, search_args: &SearchArguments) -> Result<Vec<Note>, Box<dyn Error>> {
        let mut ret: Vec<Note> = vec![];
        let conn = get_db_connection()?;
        let mut stmt = conn.prepare(generate_sql(search_args).as_str())?;

        stmt.query_map(
            params![search_args.offset, search_args.limit],
            |row| {
                let title = row.get(0)?;
                let content = row.get(1)?;
                Ok(Note::new(title, content))
            })?.for_each(|note| {
            if note.is_ok() {
                ret.push(note.unwrap())
            }
        });

        Ok(ret)
    }
}


