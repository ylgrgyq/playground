package parser

import (
	"ast"
	"lexer"
	"strconv"
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

func TestParseLiteralExpression(t *testing.T) {
	tests := []struct {
		input       string
		expectValue interface{}
	}{
		{"123123;", 123123},
		{"hello;", "hello"},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)

		express, ok := program.Statements[0].(*ast.ExpressionStatement)
		if !ok {
			t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
		}

		if !testLiteralExpression(t, express.Value, test.expectValue) {
			t.FailNow()
		}
	}
}

func testLiteralExpression(t *testing.T, expression ast.Expression, expectValue interface{}) bool {
	switch v := expectValue.(type) {
	case int:
		return testIntegerExpression(t, expression, int64(v))
	case int64:
		return testIntegerExpression(t, expression, v)
	case string:
		return testIdentifierExpression(t, expression, v)
	}

	t.Errorf("unknown expected literal type %T", expectValue)
	return false
}

func testIntegerExpression(t *testing.T, expression ast.Expression, v int64) bool {
	integerExpress, ok := expression.(*ast.Integer)
	if !ok {
		t.Errorf("expression is not *ast.Integer. got '%T'", expression)
		return false
	}

	if integerExpress.Value != v {
		t.Errorf("value for integer expression is not %d. got '%d'", v, integerExpress.Value)
		return false
	}

	if integerExpress.TokenLieteral() != strconv.FormatInt(v, 10) {
		t.Errorf("token literal for integer expression is not %d. got '%s'", v, integerExpress.TokenLieteral())
		return false
	}

	return true
}

func testIdentifierExpression(t *testing.T, expression ast.Expression, v string) bool {
	ident, ok := expression.(*ast.Identifier)
	if !ok {
		t.Errorf("expression is not *ast.Integer. got '%T'", expression)
		return false
	}

	if ident.Value != v {
		t.Errorf("value for integer expression is not %s. got '%s'", v, ident.Value)
		return false
	}

	if ident.TokenLieteral() != v {
		t.Errorf("token literal for integer expression is not %s. got '%s'", v, ident.TokenLieteral())
		return false
	}

	return true
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
