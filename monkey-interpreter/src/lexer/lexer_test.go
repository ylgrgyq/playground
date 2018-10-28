package lexer

import (
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
		{token.ASSIGN, "="},
		{token.ASSIGN, "="},
		{token.ASSIGN, "="},
		{token.ASSIGN, "="},
		{token.ASSIGN, "="},
		{token.SEMICOLON, ";"},
	}

	l := New(input)
	for i, test := range tests {
		token = l.NextToken()
		if token.Type != test.expectedType {
			t.Fatalf("tests[%d] - token type wrong. expected=%q, got=%q",
				i, test.expectedType, token.Type)
		}

		if token.Literal != test.expectedLiteral {
			t.Fatalf("tests[%d] - token literal wrong. expected=%q, got=%q",
				i, test.expectedLiteral, token.Literal)
		}
	}

}
