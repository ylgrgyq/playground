use strum_macros::{EnumString, EnumIter, IntoStaticStr};
use regex::Regex;
use lazy_static::lazy_static;

#[derive(Debug, EnumString, EnumIter, IntoStaticStr)]
pub enum FieldOfNote {
    WHOLE,
    TITLE,
    HEADERS,
}

pub struct Note {
    title: String,
    headers: Vec<String>,
    whole: String,
}

impl Note {
    pub fn new(title: String, whole: String) -> Note {
        lazy_static! {
            static ref MATCHER: Regex = Regex::new("^#+ ").expect("Error initializing regex for note headers");
        }
        let mut headers = vec![];
        for line in whole.lines() {
            if MATCHER.is_match(line) {
                headers.push(String::from(line));
            }
        }

        Note { title, whole, headers }
    }

    pub fn into_field(self, field: &FieldOfNote) -> String {
        match field {
            FieldOfNote::TITLE => self.title,
            FieldOfNote::WHOLE => self.whole,
            FieldOfNote::HEADERS => {
                self.headers.join("\n")
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_note_with_empty_header() {
        let title = "Title";
        let content = "Content";
        let note = Note::new(String::from(title), String::from(content));

        assert_eq!(title, note.title);
        assert_eq!(content, note.whole);
        assert!(note.headers.is_empty())
    }

    #[test]
    fn new_note_with_header() {
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ## Second Header  \n\
        ### Third Header";
        let note = Note::new(String::from(title), String::from(content));

        assert_eq!(title, note.title);
        assert_eq!(content, note.whole);
        assert_eq!("# Header Line", note.headers.get(0).unwrap());
        assert_eq!("## Second Header  ", note.headers.get(1).unwrap());
        assert_eq!("### Third Header", note.headers.get(2).unwrap());
    }

    #[test]
    fn into_title() {
        let title = "Title";
        let content = "Content";
        let note = Note::new(String::from(title), String::from(content));

        assert_eq!(title, note.into_field(&FieldOfNote::TITLE))
    }

    #[test]
    fn into_content() {
        let title = "Title";
        let content = "Content";
        let note = Note::new(String::from(title), String::from(content));

        assert_eq!(content, note.into_field(&FieldOfNote::WHOLE))
    }

    #[test]
    fn into_empty_headers() {
        let title = "Title";
        let content = "Content";
        let note = Note::new(String::from(title), String::from(content));

        assert!(note.into_field(&FieldOfNote::HEADERS).is_empty())
    }

    #[test]
    fn into_headers() {
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ## Second Header  \n\
        ### Third Header";
        let note = Note::new(String::from(title), String::from(content));

        assert_eq!("# Header Line\n## Second Header  \n### Third Header", note.into_field(&FieldOfNote::HEADERS));
    }
}
