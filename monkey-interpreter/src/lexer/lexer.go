package lexer

import (
	"fmt"
	"token"
)

type Lexer struct {
	input        string
	position     int
	readPosition int
	ch           byte
	line         int
	column       int
}

func New(input string) *Lexer {
	l := &Lexer{input: input, line: 1, column: 0}
	return l
}

func (l *Lexer) NextToken() token.Token {
	l.readChar()
	l.skipWhiteSpaces()

	line := l.line
	column := l.column
	var tok token.Token
	switch l.ch {
	case '=':
		if l.peekChar() == '=' {
			tok = newToken(token.EQ, "==")
			l.readChar()
		} else {
			tok = newToken(token.ASSIGN, "=")
		}
	case '!':
		if l.peekChar() == '=' {
			tok = newToken(token.NOTEQ, "!=")
			l.readChar()
		} else {
			tok = newToken(token.BANG, "!")
		}
	case '<':
		tok = newToken(token.LT, "<")
	case '>':
		tok = newToken(token.GT, ">")
	case '*':
		tok = newToken(token.ASTERISK, "*")
	case '-':
		if l.peekChar() == '-' {
			tok = newToken(token.MINUSMINUS, "--")
			l.readChar()
		} else {
			tok = newToken(token.MINUS, "-")
		}
	case '+':
		if l.peekChar() == '+' {
			tok = newToken(token.PLUSPLUS, "++")
			l.readChar()
		} else {
			tok = newToken(token.PLUS, "+")
		}
	case '/':
		tok = newToken(token.DIVIDE, "/")
	case ';':
		tok = newToken(token.SEMICOLON, ";")
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
	case '"':
		literal, err := l.readString()
		if err != nil {
			tok = newToken(token.ILLEGAL, err.Error())
		} else {
			tok = newToken(token.STRING, literal)
		}
	case 0:
		tok = newToken(token.EOF, "")
	default:
		if isLetter(l.ch) {
			literal := l.readIdentifier()
			tok = newToken(token.LookupIdent(literal), literal)
		} else if isNumber(l.ch) {
			tok = newToken(token.INT, l.readInt())
		} else {
			tok = newToken(token.ILLEGAL, string(l.ch))
		}
	}

	tok.Column = column
	tok.Line = line
	return tok
}

func (l *Lexer) readString() (string, error) {
	var ret string
Loop:
	for {
		l.readChar()
		switch l.ch {
		case 0:
			return "", fmt.Errorf("EOF while reading string")
		case '\\':
			l.readChar()
			var nextCh int
			switch l.ch {
			case 0:
				return "", fmt.Errorf("EOF while reading string")
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
				return "", fmt.Errorf("Unsupported escape character: \\%c", nextCh)
			}

			ret = ret + string(nextCh)
		case '"':
			break Loop
		default:
			ret = ret + string(l.ch)
		}
	}
	return ret, nil
}

func (l *Lexer) readChar() {
	if l.readPosition >= len(l.input) {
		l.ch = 0
	} else {
		l.ch = l.input[l.readPosition]
	}
	l.position = l.readPosition
	l.readPosition++
	l.column++
}

func (l *Lexer) peekChar() byte {
	if l.readPosition >= len(l.input) {
		return 0
	}

	return l.input[l.readPosition]
}

func (l *Lexer) skipWhiteSpaces() {
	for l.ch == ' ' || l.ch == '\t' || l.ch == '\n' || l.ch == '\r' {
		lastC := l.ch
		l.readChar()
		if lastC == '\n' || lastC == '\r' {
			l.column = 1
			l.line++
		}
	}

}

func newToken(tokenType token.TokenType, literal string) token.Token {
	return token.Token{Type: tokenType, Literal: literal}
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
