use std::time::Duration;

#[derive(Debug)]
pub struct Config {
    pub name: String,
    pub tick_interval: Duration,
    pub probe_interval: Duration,
    pub probe_timeout: Duration,
    pub suspect_timeout: Duration,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            name: String::from(""),
            tick_interval: Duration::from_secs(10),
            probe_interval: Duration::from_secs(10),
            probe_timeout: Duration::from_secs(10),
            suspect_timeout: Duration::from_secs(10),
        }
    }
}

impl Config {
    fn new(name: String) -> Config {
        Config {
            name,
            ..Default::default()
        }
    }


}


