use std::sync::mpsc::SyncSender;
use std::time::{SystemTime, Duration};
use core::fmt;
use std::fmt::Formatter;

struct Job {
    execute: Box<dyn Fn() -> ()>,
    next_timeout: SystemTime,
    execute_interval: Duration,
}

#[derive(Default)]
pub struct Scheduler<> {
    jobs: Vec<Job>,
}

impl fmt::Debug for Scheduler {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.debug_struct("Scheduler").field("size", &self.jobs.len()).finish()
    }
}

impl Scheduler {
    pub fn new() -> Scheduler {
        Scheduler {
            jobs: vec![]
        }
    }

    pub fn add_job(&mut self, execute: Box<dyn Fn() -> ()>, init_delay: Duration, execute_interval: Duration) {
        let now = SystemTime::now();
        self.jobs.push(Job {
            execute,
            execute_interval,
            next_timeout: now + init_delay,
        });
    }

    pub fn tick(&mut self) {
        let now = SystemTime::now();
        for job in &mut self.jobs {
            if now > job.next_timeout {
                (job.execute)();
                job.next_timeout = now + job.execute_interval;
            }
        }
    }
}