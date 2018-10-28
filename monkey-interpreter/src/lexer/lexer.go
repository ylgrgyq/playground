package lexer

import "token"

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

func (l *Lexer) readChar() {
	if l.readPosition >= len(l.input) {
		l.ch = 0
	} else {
		l.ch = l.input[l.readPosition]
	}
	l.position = l.readPosition
	l.readPosition++
}

func newToken(tokenType token.TokenType, ch byte) token.Token {
	return token.Token{Type: tokenType, Literal: string(ch)}
}

func (l *Lexer) NextToken() token.Token {
	l.readChar()

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
	}

	return tok
}
