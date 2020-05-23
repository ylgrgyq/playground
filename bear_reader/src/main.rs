extern crate bear_reader;
extern crate strum;

fn main() {
    match bear_reader::read_bear() {
        Ok(ret) => {
            for note in ret {
                println!("{}", note);
            }
        },
        Err(error) => {
            eprintln!("Error: {:?}", error);
        }
    }

    println!("Done")
}
