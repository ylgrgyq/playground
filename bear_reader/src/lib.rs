use strum::IntoEnumIterator;
use std::str::FromStr;
use std::error::Error;
use std::env;
use std::cmp::max;
use render::{RenderHeaderMethod, RenderArguments, RenderField};
use db::SearchArguments;

mod db;
mod article;
mod render;

fn with_subcommand_search_options(app: clap::App<'static, 'static>) -> clap::App<'static, 'static> {
    app.arg(clap::Arg::with_name("Title")
                .help("Title of target articles to filter out.")
                .short("t")
                .long("title")
                .case_insensitive(false)
                .takes_value(true)
                .multiple(false),
    ).arg(clap::Arg::with_name("Offset")
        .help("Skips the Offset articles before beginning to return the articles. Used to scan lots of articles.")
        .long("offset")
        .default_value("0")
        .validator(|v| {
            if v.parse::<u32>().is_ok() {
                Ok(())
            } else {
                Err(format!("Offset should be an integer in [0, {}].", u32::MAX))
            }
        })
        .takes_value(true)
        .multiple(false)
    ).arg(clap::Arg::with_name("Limit")
        .help("Constrain the number of articles returned.")
        .long("limit")
        .default_value("20")
        .validator(|v| {
            if let Ok(limit) = v.parse::<u32>() {
                if limit != 0 {
                    return Ok(());
                }
            }
            Err(format!("Limit should be an positive integer in (0, {}]", u32::MAX))
        })
        .takes_value(true)
        .multiple(false)
    )
}

fn get_title_subcommand() -> clap::App<'static, 'static> {
    let app = clap::App::new("title")
        .setting(clap::AppSettings::DeriveDisplayOrder)
        .about("Only read title of target article(s).");
    with_subcommand_search_options(app)
}

fn get_headers_subcommand() -> clap::App<'static, 'static> {
    let app = clap::App::new("headers")
        .setting(clap::AppSettings::DeriveDisplayOrder)
        .about("Only read headers within target article(s).")
        .arg({
            let mut allowed_fields: Vec<&'static str> = vec![];
            for field in RenderHeaderMethod::iter() {
                allowed_fields.push(field.into());
            }
            clap::Arg::with_name("Method")
                .help("Method to render headers of a article. Case insensitive.\n")
                .short("r")
                .long("render-method")
                .case_insensitive(true)
                .takes_value(true)
                .multiple(false)
                .possible_values(&allowed_fields)
        });
    with_subcommand_search_options(app)
}

fn get_whole_subcommand() -> clap::App<'static, 'static> {
    let app = clap::App::new("whole")
        .setting(clap::AppSettings::DeriveDisplayOrder)
        .about("Read whole text of target article(s) including title and content.");
    with_subcommand_search_options(app)
}

fn args_app() -> clap::App<'static, 'static> {
    clap::App::new(env!("CARGO_PKG_NAME"))
        .about(env!("CARGO_PKG_DESCRIPTION"))
        .version(env!("CARGO_PKG_VERSION"))
        .version_short("v")
        .usage("bear_reader <SUBCOMMAND> [OPTIONS]")
        .settings(&[clap::AppSettings::SubcommandRequired, clap::AppSettings::DeriveDisplayOrder])
        .subcommand(get_title_subcommand())
        .subcommand(get_headers_subcommand())
        .subcommand(get_whole_subcommand())
        .after_help("EXAMPLE:\n\
        bear_reader headers -h                                   Show this help information\n\
        bear_reader headers -t \"My Fancy article\" -r markdown    Generate TOC in Markdown for article titled \"My Fancy article\"\n\
        bear_reader title --offset 20 --limit 100                Show the titles of up to 100 articles starting from 20th article\n")
}

/// parse options used to search articles
fn parse_search_options<'a>(env_args: &'a clap::ArgMatches) -> SearchArguments<'a> {
    let title = env_args.value_of("Title");
    let limit = max(env_args.value_of("Limit")
                        // unwrap must success because we checked arguments before
                        .map(|v| v.parse::<u32>().unwrap())
                        .unwrap_or(20), 1);
    let offset = env_args.value_of("Offset")
        // unwrap must success because we checked arguments before
        .map(|v| v.parse::<u32>().unwrap())
        .unwrap_or(0);

    SearchArguments::new(title, limit, offset)
}

struct ParsedArguments<'a> {
    search_arguments: SearchArguments<'a>,
    render_arguments: RenderArguments,
}

fn parse_arugments<'a>(env_args: &'a clap::ArgMatches) -> ParsedArguments<'a> {
    let (cmd, arg_match) = env_args.subcommand();
    match (cmd, arg_match) {
        ("title", Some(sub_m)) => {
            ParsedArguments {
                search_arguments: parse_search_options(sub_m),
                render_arguments: RenderArguments::new(RenderField::TITLE, None),
            }
        }
        ("whole", Some(sub_m)) => {
            ParsedArguments {
                search_arguments: parse_search_options(sub_m),
                render_arguments: RenderArguments::new(RenderField::WHOLE, None),
            }
        }
        ("headers", Some(sub_m)) => {
            ParsedArguments {
                search_arguments: parse_search_options(sub_m),
                render_arguments: RenderArguments::new(
                    RenderField::HEADERS,
                    sub_m.value_of("Method")
                        .map(|t| {
                            RenderHeaderMethod::from_str(
                                t.to_uppercase().as_str()
                            ).expect(format!("Unknown render header method:{}", t.to_uppercase()).as_str())
                        }),
                ),
            }
        }
        _ => panic!("Unknown subcommand: {}", cmd)
    }
}

pub fn read_bear() -> Result<Vec<String>, Box<dyn Error>> {
    let args = args_app().get_matches();
    let parsed_args = parse_arugments(&args);
    let articles = db::get_bear_db().search(&parsed_args.search_arguments)?;
    let ret = articles
        .into_iter()
        .map(|article| render::render_article(article, &parsed_args.render_arguments))
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