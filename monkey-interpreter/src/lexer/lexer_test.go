package lexer

import (
	"fmt"
	"testing"
	"token"
)

func TestNextToken(t *testing.T) {
	input := `=+{}(),;`

	tests := []struct {
		expectedType    token.TokenType
		expectedLiteral string
	}{
		{token.ASSIGN, "="},
		{token.PLUS, "+"},
		{token.LBRACE, "{"},
		{token.RBRACE, "}"},
		{token.LPAREN, "("},
		{token.RPAREN, ")"},
		{token.COMMA, ","},
		{token.SEMICOLON, ";"},
	}

	l := New(input)
	for i, test := range tests {
		tk := l.NextToken()
		fmt.Println(l.ch)
		if tk.Type != test.expectedType {
			t.Fatalf("tests[%d] - token type wrong. expected=%q, got=%q",
				i, test.expectedType, tk.Type)
		}

		if tk.Literal != test.expectedLiteral {
			t.Fatalf("tests[%d] - token literal wrong. expected=%q, got=%q",
				i, test.expectedLiteral, tk.Literal)
		}
	}

}
