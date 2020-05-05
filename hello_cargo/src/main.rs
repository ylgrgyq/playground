use std::env;
use std::process;
use hello_cargo::Config;


fn main() {
    let config = Config::new(env::args()).unwrap_or_else(|err| {
        eprintln!("Problem parsing arguments: {}", err);
        process::exit(1);
    });

    if let Err(e) = hello_cargo::run(config) {
        eprintln!("Application Error: {}", e);
        process::exit(1);
    }
}

