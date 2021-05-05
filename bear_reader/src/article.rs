use regex::{Regex};
use lazy_static::lazy_static;
use std::fmt::{Display, Formatter};
use std::fmt;

const MAXIMUM_HEADER_LEVEL: usize = 16;

#[derive(Debug, PartialEq)]
pub struct Header {
    pub level: usize,
    pub header: String,
}

impl Display for Header {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result<> {
        write!(f, "{} {}", "#".repeat(self.level), self.header.as_str())
    }
}

pub struct Article {
    uuid: String,
    title: String,
    headers: Vec<Header>,
    text: String,
}

fn find_header(line: &str) -> Option<Header> {
    lazy_static! {
        static ref MATCHER: Regex = Regex::new(r"(^#+)\s(.*)")
        .expect("Error initializing regex for article headers");
    }

    if let Some(caps) = MATCHER.captures(line) {
        if let Some(captured_level) = caps.get(1) {
            let level = captured_level.as_str().len();
            // a line with too many '#' is not a valid header
            if level > MAXIMUM_HEADER_LEVEL {
                return None;
            }

            // a line only has '#' and blank is not a valid header
            if let Some(captured_header) = caps.get(2) {
                let header = captured_header.as_str().trim();
                if !header.is_empty() {
                    return Some(Header { level, header: String::from(header) });
                }
            }
        } else {
            panic!("Regex matched a header: '{}' which is not start with '#'.", line);
        }
    }

    None
}

impl Article {
    pub fn new(uuid: String, title: String, text: String) -> Article {
        let mut headers = vec![];
        let mut in_code_block = false;
        let mut headers_in_code_block = vec![];
        for line in text.lines() {
            // skip headers in code block
            if line.starts_with("```") {
                if in_code_block {
                    headers_in_code_block.clear();
                }
                in_code_block = !in_code_block;
                continue;
            }

            find_header(line).map(|header| {
                let headers_to_push = if in_code_block { &mut headers_in_code_block } else { &mut headers };
                headers_to_push.push(header);
            });
        }

        // headers in an unfinished code block is valid headers
        // and should be added to valid header list
        if in_code_block {
            headers.append(&mut headers_in_code_block);
        }

        Article { uuid, title, headers, text }
    }

    pub fn uuid_ref(&self) -> &String {
        &self.uuid
    }

    pub fn title(self) -> String {
        self.title
    }

    pub fn headers(self) -> Vec<Header> {
        self.headers
    }

    pub fn headers_ref(&self) -> &Vec<Header> {
        &self.headers
    }

    pub fn text(self) -> String {
        self.text
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_article_with_empty_header() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!(uuid, article.uuid);
        assert_eq!(title, article.title);
        assert_eq!(content, article.text);
        assert!(article.headers.is_empty())
    }

    #[test]
    fn new_article_with_header() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        #####\n\
        ##        \n\
        ##  Second Header  \n\
        ### Third Header\n\
        ";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!(uuid, article.uuid);
        assert_eq!(title, article.title);
        assert_eq!(content, article.text);
        assert_eq!("Header Line", article.headers.get(0).unwrap().header);
        assert_eq!(1, article.headers.get(0).unwrap().level);
        assert_eq!("Second Header", article.headers.get(1).unwrap().header);
        assert_eq!(2, article.headers.get(1).unwrap().level);
        assert_eq!("Third Header", article.headers.get(2).unwrap().header);
        assert_eq!(3, article.headers.get(2).unwrap().level);
        assert_eq!(3, article.headers.len());
    }

    #[test]
    fn no_headers_in_code_block() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ```\n\
        #####\n\
        ##        \n\
        ##  Second Header  \n\
        ### Third Header\n\
        ```
        ";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!(uuid, article.uuid);
        assert_eq!(title, article.title);
        assert_eq!(content, article.text);
        assert_eq!("Header Line", article.headers.get(0).unwrap().header);
        assert_eq!(1, article.headers.get(0).unwrap().level);
        assert_eq!(1, article.headers.len());
    }

    #[test]
    fn headers_in_unfinished_code_block() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ```\n\
        #####\n\
        ##        \n\
        ##  Second Header  \n\
        ### Third Header\n\
        ";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!(uuid, article.uuid);
        assert_eq!(title, article.title);
        assert_eq!(content, article.text);
        assert_eq!("Header Line", article.headers.get(0).unwrap().header);
        assert_eq!(1, article.headers.get(0).unwrap().level);
        assert_eq!("Second Header", article.headers.get(1).unwrap().header);
        assert_eq!(2, article.headers.get(1).unwrap().level);
        assert_eq!("Third Header", article.headers.get(2).unwrap().header);
        assert_eq!(3, article.headers.get(2).unwrap().level);
        assert_eq!(3, article.headers.len());
    }

    #[test]
    fn no_header_found() {
        let lines = "\
        \n\
        Hello \n\
        #\n\
        Hello World\n\
        ####              \n\
        ";

        for line in lines.lines() {
            assert!(find_header(line).is_none());
        }
    }

    #[test]
    fn trim_header() {
        let line = "\
        ###           Hi, there.  ";
        assert_eq!(Some(Header { level: 3, header: String::from("Hi, there.") }), find_header(line));
    }

    #[test]
    fn too_many_sharp_mark() {
        let line = "#".repeat(MAXIMUM_HEADER_LEVEL + 1);
        assert!(find_header(line.as_str()).is_none());
    }
}
