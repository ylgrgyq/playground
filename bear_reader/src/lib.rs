use strum::IntoEnumIterator;
use std::str::FromStr;
use std::error::Error;
use std::env;
use std::cmp::max;

mod db;
mod note;
mod render;

fn args_app() -> clap::App<'static, 'static> {
    clap::App::new(env!("CARGO_PKG_NAME"))
        .about(env!("CARGO_PKG_DESCRIPTION"))
        .version(env!("CARGO_PKG_VERSION"))
        .version_short("v")
        .arg({
            let mut allowed_fields: Vec<&'static str> = vec![];
            for field in render::RenderField::iter() {
                allowed_fields.push(field.into());
            }
            clap::Arg::with_name("Field")
                .help("Case insensitive and is required. Used to read whole or only part of a note.\n")
                .index(1)
                .case_insensitive(true)
                .takes_value(true)
                .multiple(false)
                .possible_values(&allowed_fields)
                .required(true)
        })
        .arg(clap::Arg::with_name("Title")
                 .help("Title of target notes to filter out.")
                 .short("t")
                 .long("title")
                 .case_insensitive(false)
                 .takes_value(true)
                 .multiple(false),
        )
        .arg(clap::Arg::with_name("Offset")
            .help("Skips the Offset notes before beginning to return the notes. Used to scan lots of notes.")
            .long("offset")
            .validator(|v| {
                if v.parse::<u32>().is_ok() {
                    Ok(())
                } else {
                    Err(format!("Offset should be an integer in [0, {}].", u32::max_value()))
                }
            })
            .takes_value(true)
            .multiple(false)
            .default_value("0")
        )
        .arg(clap::Arg::with_name("Limit")
            .help("Constrain the number of notes returned.")
            .long("limit")
            .default_value("20")
            .validator(|v| {
                if let Ok(limit) = v.parse::<u32>() {
                    if limit != 0 {
                        return Ok(());
                    }
                }
                Err(format!("Limit should be an positive integer in (0, {}]", u32::max_value()))
            })
            .takes_value(true)
            .multiple(false)
        )
        .after_help("EXAMPLE:\n\
        bear_reader HEADERS -t \"My Fancy Note\"\n\
        bear_reader Title --offset 20 --limit 100\n")
}

#[derive(Debug)]
pub struct SearchArguments<'a> {
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

fn get_render_field(args: &clap::ArgMatches) -> render::RenderField {
    if let Some(field) = args.value_of("Field") {
        if let Ok(field) = render::RenderField::from_str(field.to_uppercase().as_str()) {
            field
        } else {
            panic!("invalid argument for \"Field\": {}", field)
        }
    } else {
        panic!("required argument: \"Field\" has not provided")
    }
}

pub fn read_bear() -> Result<Vec<String>, Box<dyn Error>> {
    let args = args_app().get_matches();
    let search_args = SearchArguments::new(&args);

    let notes = db::get_bear_db().search(&search_args)?;
    let ret = notes
        .into_iter()
        .map(|note| render::render_note(note, &get_render_field(&args)))
        .collect();
    Ok(ret)
}

/// Reset the signal pipe (`SIGPIPE`) handler to the default one provided by the system.
/// This will end the program on `SIGPIPE` instead of panicking.
///
/// This should be called before calling any cli method or printing any output.
/// See: https://github.com/rust-lang/rust/issues/46016
pub fn reset_signal_pipe_handler() {
    #[cfg(target_family = "unix")]
        {
            use nix::sys::signal;

            unsafe {
                signal::signal(signal::Signal::SIGPIPE, signal::SigHandler::SigDfl)
                    .expect("Reset handler for `SIGPIPE` failed");
            }
        }
}