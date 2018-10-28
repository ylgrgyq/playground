package lexer

import (
	"token"
)

type Lexer struct {
	input        string
	position     int
	readPosition int
	ch           byte
}

func New(input string) *Lexer {
	l := &Lexer{input: input}
	return l
}

func (l *Lexer) NextToken() token.Token {
	l.readChar()
	l.skipWhiteSpaces()

	var tok token.Token
	switch l.ch {
	case '=':
		if l.peekChar() == '=' {
			tok.Literal = "=="
			tok.Type = token.EQ
			l.readChar()
		} else {
			tok = newToken(token.ASSIGN, '=')
		}
	case '!':
		if l.peekChar() == '=' {
			tok.Literal = "!="
			tok.Type = token.NOTEQ
			l.readChar()
		} else {
			tok = newToken(token.BANG, '!')
		}
	case '<':
		tok = newToken(token.LT, '<')
	case '>':
		tok = newToken(token.GT, '>')
	case '*':
		tok = newToken(token.ASTERISK, '*')
	case '-':
		tok = newToken(token.MINUS, '-')
	case '/':
		tok = newToken(token.DIVIDE, '/')
	case ';':
		tok = newToken(token.SEMICOLON, ';')
	case '(':
		tok = newToken(token.LPAREN, '(')
	case ')':
		tok = newToken(token.RPAREN, ')')
	case '{':
		tok = newToken(token.LBRACE, '{')
	case '}':
		tok = newToken(token.RBRACE, '}')
	case '+':
		tok = newToken(token.PLUS, '+')
	case ',':
		tok = newToken(token.COMMA, ',')
	case 0:
		tok.Literal = ""
		tok.Type = token.EOF
	default:
		if isLetter(l.ch) {
			tok.Literal = l.readIdentifier()
			tok.Type = token.LookupIdent(tok.Literal)
		} else if isNumber(l.ch) {
			tok.Literal = l.readInt()
			tok.Type = token.INT
		} else {
			tok = newToken(token.ILLEGAL, l.ch)
		}
	}

	return tok
}

func (l *Lexer) readChar() {
	if l.readPosition >= len(l.input) {
		l.ch = 0
	} else {
		l.ch = l.input[l.readPosition]
	}
	l.position = l.readPosition
	l.readPosition++
}

func (l *Lexer) peekChar() byte {
	if l.readPosition >= len(l.input) {
		return 0
	}

	return l.input[l.readPosition]
}

func (l *Lexer) skipWhiteSpaces() {
	for l.ch == ' ' || l.ch == '\t' || l.ch == '\n' || l.ch == '\r' {
		l.readChar()
	}
}

func newToken(tokenType token.TokenType, ch byte) token.Token {
	return token.Token{Type: tokenType, Literal: string(ch)}
}

func isLetter(ch byte) bool {
	return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch == '_')
}

func (l *Lexer) readIdentifier() string {
	pos := l.position
	for isLetter(l.peekChar()) {
		l.readChar()
	}
	return l.input[pos:l.readPosition]
}

func isNumber(ch byte) bool {
	return ch >= '0' && ch <= '9'
}

func (l *Lexer) readInt() string {
	pos := l.position
	for isNumber(l.peekChar()) {
		l.readChar()
	}

	return l.input[pos:l.readPosition]
}
