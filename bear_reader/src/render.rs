use strum_macros::{EnumString, EnumIter, IntoStaticStr};
use percent_encoding::{utf8_percent_encode, NON_ALPHANUMERIC};
use crate::article::{Article, Header};
use itertools::Itertools;

#[derive(Debug, EnumString, EnumIter, IntoStaticStr)]
pub enum RenderField {
    WHOLE,
    TITLE,
    HEADERS,
}

#[derive(Debug, EnumString, EnumIter, IntoStaticStr)]
pub enum RenderHeaderMethod {
    MARKDOWN,
    BEAR,
}

pub struct RenderArguments {
    render_field: RenderField,
    render_headers_method: Option<RenderHeaderMethod>,
}

impl RenderArguments {
    pub fn new(render_field: RenderField, render_headers_method: Option<RenderHeaderMethod>) -> RenderArguments {
        RenderArguments { render_field, render_headers_method }
    }
}

fn render_header<F>(header: &Header, anchor_generator: &F) -> String
    where F: Fn(&Header) -> String {
    format!("{}* [{}]({})", "\t".repeat(header.level - 1), header.header, anchor_generator(header))
}

fn render_headers<F>(article: &Article, anchor_generator: F) -> String
    where F: Fn(&Header) -> String {
    let headers = article.headers_ref();

    let mut ret = vec![];
    for header in headers {
        let header_str = render_header(&header, &anchor_generator);
        ret.push(header_str);
    }

    ret.join("\n")
}

pub fn render_article(article: Article, render_args: &RenderArguments) -> String {
    match render_args.render_field {
        RenderField::TITLE => article.title(),
        RenderField::WHOLE => article.text(),
        RenderField::HEADERS => {
            if let Some(method) = render_args.render_headers_method.as_ref() {
                match method {
                    RenderHeaderMethod::MARKDOWN => {
                        render_headers(&article, |header| {
                            format!("#{}", utf8_percent_encode(header.header.as_str(),
                                                               NON_ALPHANUMERIC).to_string())
                        })
                    }
                    RenderHeaderMethod::BEAR => {
                        render_headers(&article, |header| {
                            format!("bear://x-callback-url/open-note?id={}&header={}",
                                    article.uuid_ref(),
                                    utf8_percent_encode(header.header.as_str(),
                                                        NON_ALPHANUMERIC).to_string())
                        })
                    }
                }
            } else {
                article.headers().iter().join("\n")
            }
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
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));


        assert_eq!(title,
                   render_article(article,
                               &RenderArguments { render_field: RenderField::TITLE, render_headers_method: None }))
    }

    #[test]
    fn into_content() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!(content,
                   render_article(article, &RenderArguments { render_field: RenderField::WHOLE, render_headers_method: None }))
    }

    #[test]
    fn into_empty_headers() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert!(render_article(article,
                            &RenderArguments { render_field: RenderField::HEADERS, render_headers_method: None })
            .is_empty())
    }

    #[test]
    fn into_plain_headers() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ## Second Header  \n\
        ### Third Header";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!("# Header Line\n## Second Header\n### Third Header",
                   render_article(article, &RenderArguments { render_field: RenderField::HEADERS, render_headers_method: None }));
    }

    #[test]
    fn into_markdown_headers() {
        let uuid = "UUID 11";
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ## Second Header  \n\
        ### Third Header";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!("* [Header Line](#Header%20Line)\n\t* [Second Header](#Second%20Header)\n\t\t* [Third Header](#Third%20Header)",
                   render_article(article,
                               &RenderArguments {
                                   render_field: RenderField::HEADERS,
                                   render_headers_method: Some(RenderHeaderMethod::MARKDOWN),
                               }));
    }

    #[test]
    fn into_bear_headers() {
        let uuid = "UUID_11";
        let title = "Title";
        let content = "Content\n\
        # Header Line\n\
        Hello\n\
        \n\
        ## Second Header  \n\
        ### Third Header";
        let article = Article::new(String::from(uuid), String::from(title), String::from(content));

        assert_eq!("\
        * [Header Line](bear://x-callback-url/open-note?id=UUID_11&header=Header%20Line)\n\
        \t* [Second Header](bear://x-callback-url/open-note?id=UUID_11&header=Second%20Header)\n\
        \t\t* [Third Header](bear://x-callback-url/open-note?id=UUID_11&header=Third%20Header)",
                   render_article(article,
                               &RenderArguments {
                                   render_field: RenderField::HEADERS,
                                   render_headers_method: Some(RenderHeaderMethod::BEAR),
                               }));
    }
}
