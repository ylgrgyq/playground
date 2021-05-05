extern crate bear_reader;

fn main() {
    bear_reader::reset_signal_pipe_handler();

    match bear_reader::read_bear() {
        Ok(ret) => {
            println!("{}", ret.join("\n ----------------------------------------------------------------------- \n"));
        }
        Err(error) => {
            eprintln!("Error: {:?}", error);
        }
    }
}
