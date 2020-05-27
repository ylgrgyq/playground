use strum_macros::{EnumString, EnumIter, IntoStaticStr};
use percent_encoding::{utf8_percent_encode, NON_ALPHANUMERIC};
use crate::note::{Note, Header};
use itertools::Itertools;

#[derive(Debug, EnumString, EnumIter, IntoStaticStr)]
pub enum RenderField {
    WHOLE,
    TITLE,
    HEADERS,
    HEADERSMARKDOWN,
    HEADERSBEAR,
}

fn render_header<F>(header: &Header, anchor_generator: &F) -> String
    where F: Fn(&Header) -> String {
    format!("{}* [{}]({})", "\t".repeat(header.level - 1), header.header, anchor_generator(header))
}

fn render_headers<F>(note: &Note, anchor_generator: F) -> String
    where F: Fn(&Header) -> String {
    let headers = note.headers_ref();

    let mut ret = vec![];
    for header in headers {
        let header_str = render_header(&header, &anchor_generator);
        ret.push(header_str);
    }

    ret.join("\n")
}

pub fn render_note(note: Note, field: &RenderField) -> String {
    match field {
        RenderField::TITLE => note.title(),
        RenderField::WHOLE => note.text(),
        RenderField::HEADERS => note.headers().iter().join("\n"),
        RenderField::HEADERSMARKDOWN => {
            render_headers(&note, |header| {
                format!("#{}", utf8_percent_encode(header.header.as_str(),
                                                   NON_ALPHANUMERIC).to_string())
            })
        }
        RenderField::HEADERSBEAR => {
            render_headers(&note, |header| {
                format!("bear://x-callback-url/open-note?id={}&header={}",
                        note.uuid_ref(),
                        utf8_percent_encode(header.header.as_str(),
                                            NON_ALPHANUMERIC).to_string())
            })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn into_title() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content";
        let note = Note::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!(title, render_note(note, &RenderField::TITLE))
    }

    #[test]
    fn into_content() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content";
        let note = Note::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!(content, render_note(note, &RenderField::WHOLE))
    }

    #[test]
    fn into_empty_headers() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content";
        let note = Note::new(String::from(uuid), String::from(title), String::from(content));

        assert!(render_note(note, &RenderField::HEADERSMARKDOWN).is_empty())
    }

    #[test]
    fn into_headers() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ## Second Header  \n\
        ### Third Header";
        let note = Note::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!("# Header Line\n## Second Header  \n### Third Header", render_note(note, &RenderField::HEADERSMARKDOWN));
    }
}
