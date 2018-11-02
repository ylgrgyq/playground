package lexer

import (
	"fmt"
	"testing"
	"token"
)

func TestNextToken(t *testing.T) {
	input := `let five = 50;
	let ten = 10;

	let add = fn(x, y) {
		x + y;
	};

	let result = add(five, ten);

	! -/*5;
	5 < 10 > 5;

	if (5 < 10) {
		return true;
	} else {
		return false;
	}

	10 == 9
	10 != 9
	
	plusplus++;
	minusminus--
	--minus
	++plus`

	tests := []struct {
		expectedType    token.TokenType
		expectedLiteral string
	}{
		{token.LET, "let"},
		{token.IDENT, "five"},
		{token.ASSIGN, "="},
		{token.INT, "50"},
		{token.SEMICOLON, ";"},

		{token.LET, "let"},
		{token.IDENT, "ten"},
		{token.ASSIGN, "="},
		{token.INT, "10"},
		{token.SEMICOLON, ";"},

		{token.LET, "let"},
		{token.IDENT, "add"},
		{token.ASSIGN, "="},
		{token.FUNCTION, "fn"},
		{token.LPAREN, "("},
		{token.IDENT, "x"},
		{token.COMMA, ","},
		{token.IDENT, "y"},
		{token.RPAREN, ")"},
		{token.LBRACE, "{"},
		{token.IDENT, "x"},
		{token.PLUS, "+"},
		{token.IDENT, "y"},
		{token.SEMICOLON, ";"},
		{token.RBRACE, "}"},
		{token.SEMICOLON, ";"},

		{token.LET, "let"},
		{token.IDENT, "result"},
		{token.ASSIGN, "="},
		{token.IDENT, "add"},
		{token.LPAREN, "("},
		{token.IDENT, "five"},
		{token.COMMA, ","},
		{token.IDENT, "ten"},
		{token.RPAREN, ")"},
		{token.SEMICOLON, ";"},

		// ! -/*5;
		{token.BANG, "!"},
		{token.MINUS, "-"},
		{token.DIVIDE, "/"},
		{token.ASTERISK, "*"},
		{token.INT, "5"},
		{token.SEMICOLON, ";"},

		// 5 < 10 > 5;
		{token.INT, "5"},
		{token.LT, "<"},
		{token.INT, "10"},
		{token.GT, ">"},
		{token.INT, "5"},
		{token.SEMICOLON, ";"},

		/*
			if (5 < 10) {
				return true;
			} else {
				return false;
			}
		*/
		{token.IF, "if"},
		{token.LPAREN, "("},
		{token.INT, "5"},
		{token.LT, "<"},
		{token.INT, "10"},
		{token.RPAREN, ")"},
		{token.LBRACE, "{"},
		{token.RETURN, "return"},
		{token.TRUE, "true"},
		{token.SEMICOLON, ";"},
		{token.RBRACE, "}"},
		{token.ELSE, "else"},
		{token.LBRACE, "{"},
		{token.RETURN, "return"},
		{token.FALSE, "false"},
		{token.SEMICOLON, ";"},
		{token.RBRACE, "}"},

		/*
			10 == 9
			10 != 9
		*/
		{token.INT, "10"},
		{token.EQ, "=="},
		{token.INT, "9"},
		{token.INT, "10"},
		{token.NOTEQ, "!="},
		{token.INT, "9"},
		/*
			plusplus++;
			minusminus--
			--minus
			++plus
		*/
		{token.IDENT, "plusplus"},
		{token.PLUSPLUS, "++"},
		{token.SEMICOLON, ";"},
		{token.IDENT, "minusminus"},
		{token.MINUSMINUS, "--"},
		{token.MINUSMINUS, "--"},
		{token.IDENT, "minus"},
		{token.PLUSPLUS, "++"},
		{token.IDENT, "plus"},

		{token.EOF, ""},
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
