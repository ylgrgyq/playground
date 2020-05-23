use strum::IntoEnumIterator;
use std::str::FromStr;
use std::error::Error;
use std::env;
use std::cmp::max;

mod db;
mod note;

trait BearDb {
    fn search(&self, search_args: &SearchArguments) -> Result<Vec<note::Note>, Box<dyn Error>>;
}

fn parse_args() -> clap::ArgMatches<'static> {
    clap::App::new(env!("CARGO_PKG_NAME"))
        .about(env!("CARGO_PKG_DESCRIPTION"))
        .author(env!("CARGO_PKG_AUTHORS"))
        .version(env!("CARGO_PKG_VERSION"))
        .version_short("v")
        .arg({
            let mut allowed_fields: Vec<&'static str> = vec![];
            for field in note::FieldOfNote::iter() {
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
                     if let Ok(limit) = v.parse::<u32>() {
                         if limit == 0 {
                             Err(String::from("Limit should be greater than zero"))
                         } else {
                             Ok(())
                         }
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

#[derive(Debug)]
struct SearchArguments<'a> {
    title: Option<&'a str>,
    limit: u32,
    offset: u32,
}

impl<'a> SearchArguments<'a> {
    fn new(env_args: &'a clap::ArgMatches) -> SearchArguments<'a> {
        let title = env_args.value_of("Title");
        let limit = max(env_args.value_of("Limit")
                            .map(|v| v.parse::<u32>().unwrap())
                            .unwrap_or(20), 1);

        let offset = env_args.value_of("Offset")
            .map(|v| v.parse::<u32>().unwrap())
            .unwrap_or(0);

        SearchArguments { title, limit, offset }
    }
}

fn get_search_field(args: &clap::ArgMatches) -> note::FieldOfNote {
    if let Some(field) = args.value_of("Field") {
        if let Ok(field) = note::FieldOfNote::from_str(field.to_uppercase().as_str()) {
            field
        } else {
            panic!("invalid argument for \"Field\": {}", field)
        }
    } else {
        panic!("required argument: \"Field\" has not provided")
    }
}

fn get_bear_db() -> Box<dyn BearDb> {
    #[cfg(target_os = "macos")]
        let beardb = db::sqlite_db::SqliteBearDb {};
    Box::new(beardb)
}

pub fn read_bear() -> Result<Vec<String>, Box<dyn Error>> {
    let args = parse_args();
    let search_args = SearchArguments::new(&args);

    let notes = get_bear_db().search(&search_args)?;
    let ret = notes
        .into_iter()
        .map(|note| note.into_field(&get_search_field(&args)))
        .collect();
    Ok(ret)
}