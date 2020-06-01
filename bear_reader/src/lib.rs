use strum::IntoEnumIterator;
use std::str::FromStr;
use std::error::Error;
use std::env;
use std::cmp::max;
use render::{RenderHeaderMethod, RenderArguments, RenderField};
use db::SearchArguments;

mod db;
mod note;
mod render;

fn args_app() -> clap::App<'static, 'static> {
    clap::App::new(env!("CARGO_PKG_NAME"))
        .about(env!("CARGO_PKG_DESCRIPTION"))
        .version(env!("CARGO_PKG_VERSION"))
        .version_short("v")
        .usage("bear_reader <SUBCOMMAND> [OPTIONS]")
        .settings(&[clap::AppSettings::SubcommandRequired, clap::AppSettings::DeriveDisplayOrder])
        .subcommand(clap::App::new("title")
            .about("Only read title of target note(s).")
        )
        .subcommand(clap::App::new("headers")
            .about("Only read headers within target note(s).")
            .arg({
                let mut allowed_fields: Vec<&'static str> = vec![];
                for field in RenderHeaderMethod::iter() {
                    allowed_fields.push(field.into());
                }
                clap::Arg::with_name("Method")
                    .help("Method to render headers of a note. Case insensitive.\n")
                    .short("r")
                    .long("render-method")
                    .case_insensitive(true)
                    .takes_value(true)
                    .multiple(false)
                    .possible_values(&allowed_fields)
            }))
        .subcommand(clap::App::new("whole")
            .about("Read whole text of target note(s) including title and content."))
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
        ).after_help("EXAMPLE:\n\
        bear_reader headers -h\n\
        bear_reader headers -t \"My Fancy Note\"\n\
        bear_reader title --offset 20 --limit 100\n")
}

fn get_search_args<'a>(env_args: &'a clap::ArgMatches) -> SearchArguments<'a> {
    let title = env_args.value_of("Title");
    let limit = max(env_args.value_of("Limit")
                        .map(|v| v.parse::<u32>().unwrap())
                        .unwrap_or(20), 1);

    let offset = env_args.value_of("Offset")
        .map(|v| v.parse::<u32>().unwrap())
        .unwrap_or(0);

    SearchArguments::new(title, limit, offset)
}

fn get_render_args(args: &clap::ArgMatches) -> RenderArguments {
    let (cmd, arg_match) = args.subcommand();
    match (cmd, arg_match) {
        ("title", Some(_)) => {
            RenderArguments::new(RenderField::TITLE, None)
        }
        ("whole", Some(_)) => {
            RenderArguments::new(RenderField::WHOLE, None)
        }
        ("headers", Some(sub_m)) => {
            RenderArguments::new(
                RenderField::HEADERS,
                sub_m.value_of("Method")
                    .map(|t| {
                        RenderHeaderMethod::from_str(
                            t.to_uppercase().as_str()
                        ).expect(format!("Unknown render header method:{}", t.to_uppercase()).as_str())
                    }),
            )
        }
        _ => panic!(format!("Unknown subcommand: {}", cmd))
    }
}

pub fn read_bear() -> Result<Vec<String>, Box<dyn Error>> {
    let args = args_app().get_matches();
    let search_args = get_search_args(&args);

    let notes = db::get_bear_db().search(&search_args)?;
    let ret = notes
        .into_iter()
        .map(|note| render::render_note(note, &get_render_args(&args)))
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