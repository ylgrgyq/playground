use rusqlite::{Connection, NO_PARAMS};
use strum::IntoEnumIterator;
use strum_macros::{EnumString, EnumIter, IntoStaticStr};
use std::str::FromStr;
use std::error::Error;
use std::io;
use std::env;
use std::fmt;
use std::fmt::Formatter;

// https://github.com/alexander-lee/markdown-github-bear-toc

fn parse_args() -> clap::ArgMatches<'static> {
    clap::App::new(env!("CARGO_PKG_NAME"))
        .about(env!("CARGO_PKG_DESCRIPTION"))
        .author(env!("CARGO_PKG_AUTHORS"))
        .version(env!("CARGO_PKG_VERSION"))
        .version_short("v")
        .arg({
            let mut allowed_fields: Vec<&'static str> = vec![];
            for field in NotesFieldType::iter() {
                allowed_fields.push(field.into());
            }
            clap::Arg::with_name("Field")
                .help("field of note to read")
                .index(1)
                .case_insensitive(true)
                .takes_value(true)
                .multiple(false)
                .possible_values(&allowed_fields)
                .required(true)
        })
        .arg(clap::Arg::with_name("Title")
                 .help("Title of note to filter")
                 .short("t")
                 .long("title")
                 .case_insensitive(true)
                 .takes_value(true)
                 .multiple(false),
        )
        .arg(clap::Arg::with_name("Offset")
                 .help("Offset")
                 .long("offset")
                 .validator(|v| {
                     if v.parse::<u32>().is_ok() {
                         Ok(())
                     } else {
                         Err(String::from("Offset should be an unsigned int"))
                     }
                 })
                 .takes_value(true)
                 .multiple(false)
                 .conflicts_with("Title"),
        )
        .arg(clap::Arg::with_name("Limit")
                 .help("Limit")
                 .long("limit")
                 .validator(|v| {
                     if v.parse::<u32>().is_ok() {
                         Ok(())
                     } else {
                         Err(String::from("Limit should be an unsigned int"))
                     }
                 })
                 .takes_value(true)
                 .multiple(false)
                 .conflicts_with("Title"),
        )
        .get_matches()
}

#[derive(Debug, EnumString, EnumIter, IntoStaticStr)]
enum NotesFieldType {
    TITLE,
    CONTENT,
}


fn get_db_connection() -> rusqlite::Result<Connection> {
    #[cfg(target_os = "macos")] let db_path = get_db_path();
    Connection::open(db_path)
}

#[cfg(target_os = "macos")]
fn get_db_path() -> String {
    let home = env!("HOME");
    let bear_db_path = "Library/Group Containers/9K33E3U3T4.net.shinyfrog.bear/Application Data/database.sqlite";

    format!("{}/{}", home, bear_db_path)
}

fn search_notes(args: SearchArguments) -> rusqlite::Result<Vec<String>> {
    let conn = get_db_connection()?;
    let mut stmt = conn.prepare(args.generate_sql().as_str())?;
    let mut iter = stmt.query(NO_PARAMS)?;

    let mut ret: Vec<String> = vec![];
    while let Some(row) = iter.next()? {
        ret.push(row.get(0)?);
    }
    println!("xcxc {:?}", ret);
    Ok(ret)
}

#[derive(Debug)]
struct SearchArguments<'a> {
    field: String,
    limit: u32,
    offset: u32,
    title: Option<&'a str>,
}

fn get_search_field(args: &clap::ArgMatches) -> NotesFieldType {
    if let Some(field) = args.value_of("Field") {
        if let Ok(field) = NotesFieldType::from_str(field.to_uppercase().as_str()) {
            field
        } else {
            panic!("invalid argument for \"Field\": {}", field)
        }
    } else {
        panic!("required argument: \"Field\" has not provided")
    }
}

impl<'a> SearchArguments<'a> {
    fn new(env_args: &'a clap::ArgMatches) -> SearchArguments<'a> {
        let field = get_search_field(&env_args);
        let field = match field {
            NotesFieldType::CONTENT => "ZTEXT",
            NotesFieldType::TITLE => "ZTITLE",
        };

        let title = env_args.value_of("Title");
        let limit = env_args.value_of("Limit")
            .map(|v| v.parse::<u32>().unwrap())
            .unwrap_or(20);
        let offset = env_args.value_of("Offset")
            .map(|v| v.parse::<u32>().unwrap())
            .unwrap_or(0);

        SearchArguments { field: String::from(field), limit, offset, title }
    }

    fn generate_sql(&self) -> String {
        let title_filter = self.title.as_ref()
            .map(|title| format!(" AND `ZTITLE` == '{}'", title))
            .unwrap_or(String::from(""));
        let limit = format!("LIMIT {},{}", self.offset, self.limit);

        format!("SELECT {} FROM `ZSFNOTE` \
        WHERE `ZTRASHED` LIKE '0' AND `ZARCHIVED` LIKE '0' {} {}",
                self.field, title_filter, limit)
    }
}

pub fn read_bear() -> Result<Vec<String>, Box<dyn Error>> {
    let args = parse_args();
    let search_args = SearchArguments::new(&args);

    search_notes(search_args).map_err(|e| e.into())
}