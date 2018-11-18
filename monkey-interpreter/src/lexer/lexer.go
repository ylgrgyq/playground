package lexer

import (
	"fmt"
	"token"
	"unicode"
	"unicode/utf8"
)

type LexerParseError struct {
	Line   int
	Column int
	Msg    string
}

func (l LexerParseError) Error() string {
	return fmt.Sprintf("%s at line: %d, column: %d", l.Msg, l.Line, l.Column)
}

// A Lexer holding the state while scanning the input string
type Lexer struct {
	input        []byte // the byte array being scanned
	position     int    // the offset of the character current reading
	readPosition int    // offset after current reading character
	ch           rune   // current character

	line   int // the line number for current reading character in the input string
	column int // the column nuber for current reading character in the input string

	ErrorList []string
}

// New create a Lexer to scan the input string
func New(input string) *Lexer {
	l := &Lexer{input: []byte(input), line: 1, column: 0}
	l.readRune()
	return l
}

func (l *Lexer) error(msg string) {
	l.ErrorList = append(l.ErrorList, msg)
}

// token0 is the default token type for current l.ch
// token1 is the returned token type when l.ch equals to '='
func (l *Lexer) switch2(ch rune, token0, token1 token.TokenType) token.Token {
	if l.ch == '=' {
		tok := newToken(token1, string([]rune{ch, l.ch}))
		l.readRune()
		return tok
	}
	return newToken(token0, string(ch))
}

func (l *Lexer) switch3(ch rune, token0, token1 token.TokenType, ch2 rune, token2 token.TokenType) token.Token {
	if l.ch == '=' {
		tok := newToken(token1, string([]rune{ch, l.ch}))
		l.readRune()
		return tok
	}

	if l.ch == ch2 {
		tok := newToken(token2, string([]rune{ch, l.ch}))
		l.readRune()
		return tok
	}

	return newToken(token0, string(ch))
}

// NextToken scan and return the next token parsed from the input string
func (l *Lexer) NextToken() token.Token {
	l.skipWhiteSpaces()

	line := l.line
	column := l.column
	var tok token.Token
	switch ch := l.ch; {
	case isLetter(ch):
		literal := l.readIdentifier()
		tok = newToken(token.LookupIdent(literal), literal)
	case isDigit(ch):
		tok = newToken(token.INT, l.readInt())
	default:
		l.readRune()
		switch ch {
		case '=':
			tok = l.switch2('=', token.ASSIGN, token.EQ)
		case '!':
			tok = l.switch2('!', token.BANG, token.NOTEQ)
		case '<':
			tok = l.switch2('<', token.LT, token.LTE)
		case '>':
			tok = l.switch2('>', token.GT, token.GTE)
		case '*':
			tok = l.switch2('*', token.ASTERISK, token.ASTERISK_ASSIGN)
		case '-':
			tok = l.switch3('-', token.MINUS, token.MINUS_ASSIGN, '-', token.MINUSMINUS)
		case '+':
			tok = l.switch3('+', token.PLUS, token.PLUS_ASSIGN, '+', token.PLUSPLUS)
		case '/':
			tok = l.switch2('/', token.DIVIDE, token.DIVIDE_ASSIGN)
		case ';':
			tok = newToken(token.SEMICOLON, ";")
		case '[':
			tok = newToken(token.LBRACKET, "[")
		case ']':
			tok = newToken(token.RBRACKET, "]")
		case '(':
			tok = newToken(token.LPAREN, "(")
		case ')':
			tok = newToken(token.RPAREN, ")")
		case '{':
			tok = newToken(token.LBRACE, "{")
		case '}':
			tok = newToken(token.RBRACE, "}")
		case ',':
			tok = newToken(token.COMMA, ",")
		case ':':
			tok = newToken(token.COLON, ":")
		case '"':
			tok = l.readString()
		case 0:
			tok = newToken(token.EOF, "")
		default:
			panic(LexerParseError{Msg: "Unrecognized character", Line: line, Column: column})
		}
	}

	tok.Column = column
	tok.Line = line
	return tok
}

func (l *Lexer) readString() token.Token {
	startLine := l.line
	startColumn := l.column
	var ret []rune
Loop:
	for {
		switch l.ch {
		case 0:
			panic(LexerParseError{Msg: "EOF while reading string", Line: startLine, Column: startColumn})
		case '\\':
			l.readRune()
			var nextCh rune
			switch l.ch {
			case 0:
				panic(LexerParseError{Msg: "EOF while reading string", Line: startLine, Column: startColumn})
			case 't':
				nextCh = '\t'
			case 'n':
				nextCh = '\n'
			case 'r':
				nextCh = '\r'
			case '\'':
				nextCh = '\''
			case '"':
				nextCh = '"'
			case '\\':
				nextCh = '\\'
			default:
				panic(LexerParseError{Msg: "Unsupported escape character", Line: l.line, Column: l.column})
			}

			ret = append(ret, nextCh)
		case '"':
			l.readRune()
			break Loop
		default:
			ret = append(ret, l.ch)
		}
		l.readRune()
	}
	return newToken(token.STRING, string(ret))
}

func (l *Lexer) readRune() {
	size := 1
	if l.readPosition >= len(l.input) {
		l.ch = 0
	} else {
		l.ch = rune(l.input[l.readPosition])
		if l.ch >= utf8.RuneSelf {
			l.ch, size = utf8.DecodeRune(l.input[l.readPosition:])

			if l.ch == utf8.RuneError {
				panic(LexerParseError{Msg: "illegal utf-8 encoding"})
			}
		}
	}
	l.position = l.readPosition
	l.readPosition += size
	l.column++

	if l.ch == '\n' || l.ch == '\r' {
		l.column = 0
		l.line++
	}
}

func (l *Lexer) skipWhiteSpaces() {
	for l.ch == ' ' || l.ch == '\t' || l.ch == '\n' || l.ch == '\r' {
		l.readRune()
	}
}

func newToken(tokenType token.TokenType, literal string) token.Token {
	return token.Token{Type: tokenType, Literal: literal}
}

func isLetter(ch rune) bool {
	return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch == '_')
}

func (l *Lexer) readIdentifier() string {
	pos := l.position
	for isLetter(l.ch) {
		l.readRune()
	}
	return string(l.input[pos:l.position])
}

func isDigit(ch rune) bool {
	return '0' <= ch && ch <= '9' || ch >= utf8.RuneSelf && unicode.IsDigit(ch)
}

func (l *Lexer) readInt() string {
	pos := l.position
	l.readRune()
	for isDigit(l.ch) {
		l.readRune()
	}

	return string(l.input[pos:l.position])
}
