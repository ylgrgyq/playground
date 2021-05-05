# Bear Reader

A tool to read articles from Bear. I use it to generate TOC in Markdown or Bear format.

Currently, it's only available on MacOS.

## Usage

```
A command line tool to read articles from Bear.

USAGE:
    bear_reader <SUBCOMMAND> [OPTIONS]

FLAGS:
    -h, --help       Prints help information
    -v, --version    Prints version information

SUBCOMMANDS:
    title      Only read title of target article(s).
    headers    Only read headers within target article(s).
    whole      Read whole text of target article(s) including title and content.
    help       Prints this message or the help of the given subcommand(s)

EXAMPLE:
bear_reader headers -h                                   Show this help information
bear_reader headers -t "My Fancy article" -r markdown    Generate TOC in Markdown for article titled "My Fancy article"
bear_reader title --offset 20 --limit 100                Show the titles of up to 100 articles starting from 20th
```

For example, we have an article as:
```markdown
# Testing Article
## Title 2

### Title 2.1

## Title 3
### Title 3.1

### Title 3.2

#### Title 3.2.1

##### Title 3.2.1.1
```

To generate TOC in markdown:

```
$ bear_reader headers -t "Testing Article"
# Testing Article
## Title 2
### Title 2.1
## Title 3
### Title 3.1
### Title 3.2
#### Title 3.2.1
##### Title 3.2.1.1
```

To generate TOC in Bear format:

```
$ bear_reader headers -t "Testing Article" -r bear
* [Testing Article](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Testing%20Article)
	* [Title 2](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Title%202)
		* [Title 2.1](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Title%202%2E1)
	* [Title 3](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Title%203)
		* [Title 3.1](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Title%203%2E1)
		* [Title 3.2](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Title%203%2E2)
			* [Title 3.2.1](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Title%203%2E2%2E1)
				* [Title 3.2.1.1](bear://x-callback-url/open-note?id=83841C0D-AF52-493D-BDAC-EE567600808C-10659-000072D87825C3A7&header=Title%203%2E2%2E1%2E1)
```