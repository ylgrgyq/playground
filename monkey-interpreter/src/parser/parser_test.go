package parser

import (
	"ast"
	"lexer"
	"testing"
)

func TestLetParseStatement(t *testing.T) {
	input := `
	let x = 5;
	let niuniu = 100;
	let huahua = 1122334;
	`
	program := parseTestingProgram(t, input, 3)

	tests := []struct {
		expectedIdentifier string
	}{
		{"x"},
		{"niuniu"},
		{"huahua"},
	}

	for i, test := range tests {
		actual := program.Statements[i]

		if actual.TokenLieteral() != "let" {
			t.Errorf("token literal is not let. got '%q'", actual.TokenLieteral())
		}

		letStateMent, ok := actual.(*ast.LetStatement)
		if !ok {
			t.Errorf("statement not *ast.LetStatement. got '%T'", actual)
		}

		if letStateMent.Name.Value != test.expectedIdentifier {
			t.Errorf("actual.Name.Value not '%s'. got '%s'", test.expectedIdentifier, letStateMent.Name.Value)
		}

		if letStateMent.Name.TokenLieteral() != test.expectedIdentifier {
			t.Errorf("actual.Name not '%s'. got '%s'", test.expectedIdentifier, letStateMent.Name.TokenLieteral())
		}

	}
}

func TestParseReturnStatement(t *testing.T) {
	input := `
		return  ;
		return 1234567;
		return x;
	`
	program := parseTestingProgram(t, input, 3)

	tests := []struct {
		expect string
	}{
		{"return;"},
		{"return;"},
		{"return;"},
	}

	for i, test := range tests {
		actual := program.Statements[i]

		if actual.TokenLieteral() != "return" {
			t.Errorf("token literal is not return. got '%q'", actual.TokenLieteral())
		}

		retStateMent, ok := actual.(*ast.ReturnStatement)

		if !ok {
			t.Errorf("statement not *ast.ReturnStatement. got '%T'", actual)
		}

		if retStateMent.String() != test.expect {
			t.Errorf("parsed not expected statement '%q'. got '%q'", test.expect, actual.String())
		}
	}
}

func TestParseIdentifierExpression(t *testing.T) {
	input := "hello;"

	program := parseTestingProgram(t, input, 1)

	express, ok := program.Statements[0].(*ast.ExpressionStatement)
	if !ok {
		t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
	}

	if express.String() != "hello;" {
		t.Errorf("parsed not expected statement 'hello;'. got '%q'", express.String())
	}
}

func parseTestingProgram(t *testing.T, input string, expectedStatementCount int) *ast.Program {
	lex := lexer.New(input)
	par := New(lex)

	program := par.ParseProgram()
	if program == nil {
		t.Fatalf("ParseProgram() returns nil")
	}
	checkParserErrors(t, par)

	if len(program.Statements) != expectedStatementCount {
		t.Fatalf("program.Statements does not contain %d statements. got=%d", expectedStatementCount, len(program.Statements))
	}

	return program
}

func checkParserErrors(t *testing.T, par *Parser) {
	errors := par.Errors()
	if len(errors) == 0 {
		return
	}

	for _, msg := range errors {
		t.Errorf("parser error: %q", msg)
	}

	t.FailNow()
}
