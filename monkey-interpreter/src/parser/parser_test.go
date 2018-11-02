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

func TestParsePrefixExpression(t *testing.T) {
	tests := []struct {
		input          string
		expectOperator string
		expectValue    interface{}
	}{
		{"!123123;  ", "!", 123123},
		{"  -56;  ", "-", 56},
		{"! haha;", "!", "haha"},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)

		express, ok := program.Statements[0].(*ast.ExpressionStatement)
		if !ok {
			t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
		}

		prefix := express.Value.(*ast.PrefixExpression)
		if prefix.Operator != test.expectOperator {
			t.Errorf("expect prefix operator %q. got %q", test.expectOperator, prefix.Operator)
		}

		if !testLiteralExpression(t, prefix.Value, test.expectValue) {
			t.FailNow()
		}
	}
}

func TestParseInfixExpression(t *testing.T) {
	tests := []struct {
		input          string
		expectOperator string
		expectLeft     interface{}
		expectRight    interface{}
	}{
		{"123123 + 111;  ", "+", 123123, 111},
		{" 12 - 56;  ", "-", 12, 56},
		{" 11 * haha;", "*", 11, "haha"},
		{" 11 / haha;", "/", 11, "haha"},
		{" greater > less;", ">", "greater", "less"},
		{" less < greater;", "<", "less", "greater"},
		{" a == 5;", "==", "a", 5},
		{" b != 1;", "!=", "b", 1},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)

		express, ok := program.Statements[0].(*ast.ExpressionStatement)
		if !ok {
			t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
		}

		infix := express.Value.(*ast.InfixExpression)
		if infix.Operator != test.expectOperator {
			t.Errorf("expect prefix operator %q. got %q", test.expectOperator, infix.Operator)
		}

		if !testLiteralExpression(t, infix.Left, test.expectLeft) {
			t.FailNow()
		}

		if !testLiteralExpression(t, infix.Right, test.expectRight) {
			t.FailNow()
		}
	}
}

func TestExpressionPrecedence(t *testing.T) {
	tests := []struct {
		input        string
		expectString string
	}{
		{"123123 + 111 + 222;  ", "((123123 + 111) + 222)"},
		{" 12 - 56 - haha;  ", "((12 - 56) - haha)"},
		{" niuniu + 11 * haha;", "(niuniu + (11 * haha))"},
		{" niuniu + 11 * !haha;", "(niuniu + (11 * (!haha)))"},
		{" a * b + c", "((a * b) + c)"},
		{" a * b * c", "((a * b) * c)"},
		{" a * b / c", "((a * b) / c)"},
		{" a + b / c + d", "((a + (b / c)) + d)"},
		{" a + b / c * d * e + f", "((a + (((b / c) * d) * e)) + f)"},
		{"!-a", "(!(-a))"},
		{"a > b == c < d", "((a > b) == (c < d))"},
		{"a * b != c - d", "((a * b) != (c - d))"},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)

		express, ok := program.Statements[0].(*ast.ExpressionStatement)
		if !ok {
			t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
		}

		infix := express.Value.(ast.Expression)
		if infix.String() != test.expectString {
			t.Errorf("expect infix String() %q. got %q", test.expectString, infix.String())
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
		t.Errorf("expression is not *ast.Identifier. got '%T'", expression)
		return false
	}

	if ident.Value != v {
		t.Errorf("value for identifier expression is not %s. got '%s'", v, ident.Value)
		return false
	}

	if ident.TokenLieteral() != v {
		t.Errorf("token literal for identifier expression is not %s. got '%s'", v, ident.TokenLieteral())
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
