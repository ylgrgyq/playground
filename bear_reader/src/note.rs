use strum_macros::{EnumString, EnumIter, IntoStaticStr};

#[derive(Debug, EnumString, EnumIter, IntoStaticStr)]
pub enum FieldOfNote {
    TITLE,
    CONTENT,
}

pub struct Note {
    title: String,
    heads: Vec<String>,
    content: String,
}

impl Note {
    pub fn new(title: String, content: String) -> Note {
        Note { title, content, heads: vec![] }
    }

    pub fn into_field(self, field: &FieldOfNote) -> String {
        match field {
            FieldOfNote::TITLE => self.title,
            FieldOfNote::CONTENT => self.content,
        }
    }
}
