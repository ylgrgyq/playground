use std::time::Duration;

#[derive(Debug)]
pub struct Config {
    pub name: String,
    address: String,
    tick_interval: Duration,
    probe_interval: Duration,
    probe_timeout: Duration,
    suspect_timeout: Duration,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            name: String::from(""),
            address: String::from(""),
            tick_interval: Duration::from_secs(10),
            probe_interval: Duration::from_secs(10),
            probe_timeout: Duration::from_secs(10),
            suspect_timeout: Duration::from_secs(10),
        }
    }
}

impl Config {
    pub fn new(name: String) -> Config {
        Config {
            name,
            ..Default::default()
        }
    }

    pub fn get_name(&self) -> String {
        self.name.clone()
    }

    pub fn get_address(&self) -> String {
        self.address.clone()
    }

    pub fn get_tick_interval(&self) -> Duration {
        self.tick_interval.clone()
    }

    pub fn get_probe_interval(&self) -> Duration {
        self.probe_interval.clone()
    }

    pub fn get_probe_timeout(&self) -> Duration {
        self.probe_timeout.clone()
    }

    pub fn get_suspect_timeout(&self) -> Duration {
        self.suspect_timeout.clone()
    }
}


