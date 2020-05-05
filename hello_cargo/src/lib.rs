use std::fs;
use std::error::Error;
use std::env;

pub struct Config {
    query: String,
    filename: String,
    case_sensitive: bool,
}

impl Config {
    pub fn new(mut args: env::Args) -> Result<Config, &'static str> {
        args.next();
        let query = match args.next() {
            Some(arg) => arg,
            None => return Err("not enough arguments"),
        };

        let filename = match args.next() {
            Some(arg) => arg,
            None => return Err("not enough arguments"),
        };

        println!("query {:?} {:?}", query, filename);

        let case_sensitive = env::var("CASE_INSENSITIVE").is_err();

        Ok(Config { query, filename, case_sensitive })
    }
}

pub fn run(config: Config) -> Result<(), Box<dyn Error>> {
    let lines = fs::read_to_string(config.filename)?;

    let results = if config.case_sensitive {
        search_insensitive(&config.query, &lines)
    } else {
        search_sensitive(&config.query, &lines)
    };

    for line in results {
        println!("{}", line)
    }

    Ok(())
}

fn search_sensitive<'a>(query: &str, contents: &'a str) -> Vec<&'a str> {
    return contents.lines()
        .filter(|line| line.contains(query))
        .collect();
}

fn search_insensitive<'a>(query: &str, contents: &'a str) -> Vec<&'a str> {
    let query = query.to_lowercase();
    return contents.lines()
        .filter(|line| line.to_lowercase().contains(&query))
        .collect();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn one_result() {
        let query = "duct";
        let contents = "\
        Rust:
safe, fast, productive.
Pick three.";

        assert_eq!(
            vec!["safe, fast, productive."],
            search_sensitive(query, contents)
        )
    }

    #[test]
    fn case_sensitive() {
        let query = "duct";
        let contents = "\
        Rust:
safe, fast, productive.
Pick three.
Duct tape.";
        assert_eq!(
            vec!["safe, fast, productive."],
            search_sensitive(query, contents)
        )
    }

    #[test]
    fn case_insensitive() {
        let query = "rUst";
        let contents = "\
        Rust:
safe, fast, productive.
Pick three.
Duct tape.
Trust me.";
        assert_eq!(
            vec!["Rust:", "Trust me."],
            search_insensitive(query, contents)
        )
    }
}

