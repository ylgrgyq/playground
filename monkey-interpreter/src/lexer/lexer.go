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

}

func newToken(tokenType token.TokenType, ch byte) token.Token {
	return Token{Type: tokenType, Literal: string(ch)}
}

func (l *Lexer) NextToken() token.Token {
	var token token.Token
	token = newToken(ASSIGN, '=')
	return token
}
