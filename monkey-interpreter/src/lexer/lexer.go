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
		tok = newToken(token.ASSIGN, '=')
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
			switch tok.Literal {
			case "let":
				tok.Type = token.LET
			case "fn":
				tok.Type = token.FUNCTION
			default:
				tok.Type = token.IDENT
			}
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
	for isLetter(l.input[l.readPosition]) {
		l.readPosition++
	}
	ident := l.input[l.position:l.readPosition]
	l.position = l.readPosition - 1
	return ident
}

func isNumber(ch byte) bool {
	return ch >= '0' && ch <= '9'
}

func (l *Lexer) readInt() string {
	for isNumber(l.input[l.readPosition]) {
		l.readPosition++
	}

	n := l.input[l.position:l.readPosition]
	l.position = l.readPosition - 1
	return n
}
