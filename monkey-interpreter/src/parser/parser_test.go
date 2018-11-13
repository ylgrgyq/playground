package parser

import (
	"ast"
	"lexer"
	"strconv"
	"testing"
)

func TestLetParseStatement(t *testing.T) {
	tests := []struct {
		input                    string
		expectedIdentifier       string
		expectedExpressionString string
	}{
		{"let x = 5;", "x", "5"},
		{"let niuniu = 100;", "niuniu", "100"},
		{"let huahua = 1122334;", "huahua", "1122334"},
		{"let hello = new + world;", "hello", "(new + world)"},
		{"let y = false;", "y", "false"},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)

		letStateMent, ok := program.Statements[0].(*ast.LetStatement)
		if !ok {
			t.Errorf("statement not *ast.LetStatement. got '%T'", program.Statements[0])
		}

		if letStateMent.Name.Value != test.expectedIdentifier {
			t.Errorf("actual.Name.Value not '%s'. got '%s'", test.expectedIdentifier, letStateMent.Name.Value)
		}

		if letStateMent.Name.String() != test.expectedIdentifier {
			t.Errorf("actual.Name not '%s'. got '%s'", test.expectedIdentifier, letStateMent.Name.String())
		}

		if letStateMent.Value.String() != test.expectedExpressionString {
			t.Errorf("actual.Name not '%s'. got '%s'", test.expectedIdentifier, letStateMent.Value.String())
		}
	}
}

func TestParseReturnStatement(t *testing.T) {
	tests := []struct {
		input                    string
		expectedExpressionString string
	}{
		{"return;", "return;"},
		{"return x + y;", "return (x + y);"},
		{"return 111;", "return 111;"},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)
		retStateMent, ok := program.Statements[0].(*ast.ReturnStatement)

		if !ok {
			t.Errorf("statement not *ast.ReturnStatement. got '%T'", program.Statements[0])
		}

		if retStateMent.String() != test.expectedExpressionString {
			t.Errorf("parsed not expected statement '%q'. got '%q'", test.expectedExpressionString, retStateMent.String())
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
		{"true;", true},
		{"false;", false},
		{"\"哈哈哈\";", "哈哈哈"},
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
		{"! true;", "!", true},
		{"! false;", "!", false},
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
		{" true == true;", "==", true, true},
		{" false != false;", "!=", false, false},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)

		express, ok := program.Statements[0].(*ast.ExpressionStatement)
		if !ok {
			t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
		}

		infix := express.Value.(*ast.InfixExpression)
		if infix.Operator != test.expectOperator {
			t.Errorf("expect infix operator %q. got %q", test.expectOperator, infix.Operator)
		}

		if !testLiteralExpression(t, infix.Left, test.expectLeft) {
			t.FailNow()
		}

		if !testLiteralExpression(t, infix.Right, test.expectRight) {
			t.FailNow()
		}
	}
}

func TestParsePostfixExpression(t *testing.T) {
	tests := []struct {
		input          string
		expectOperator string
		expectLeft     interface{}
	}{
		{" a++;", "++", "a"},
		{" a--;", "--", "a"},
	}

	for _, test := range tests {
		program := parseTestingProgram(t, test.input, 1)

		express, ok := program.Statements[0].(*ast.ExpressionStatement)
		if !ok {
			t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
		}

		postfix := express.Value.(*ast.PostfixExpression)
		if postfix.Operator != test.expectOperator {
			t.Errorf("expect postfix operator %q. got %q", test.expectOperator, postfix.Operator)
		}

		if !testLiteralExpression(t, postfix.Left, test.expectLeft) {
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
		{" a * b + c;", "((a * b) + c)"},
		{" a * b * c;", "((a * b) * c)"},
		{" a * b / c;", "((a * b) / c)"},
		{" a + b / c + d;", "((a + (b / c)) + d)"},
		{" a + b / c * d * e + f;", "((a + (((b / c) * d) * e)) + f)"},
		{"!-a;", "(!(-a))"},
		{"a > b == c < d;", "((a > b) == (c < d))"},
		{"a > b == ! false;", "((a > b) == (!false))"},
		{"a < b < c  != ! true;", "(((a < b) < c) != (!true))"},
		{"a * b != c - d;", "((a * b) != (c - d))"},
		{"a-- * b++ != c----- d++;", "(((a--) * (b++)) != (((c--)--) - (d++)))"},
		{"(123123 + 111) * 222;  ", "((123123 + 111) * 222)"},
		{"a + (b + c) + d;", "((a + (b + c)) + d)"},
		{"-(a +b);", "(-(a + b))"},
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

func TestIfExpression(t *testing.T) {
	input := `if (x < y) { return x + 1;} else {return y + 1;}`

	program := parseTestingProgram(t, input, 1)

	express, ok := program.Statements[0].(*ast.ExpressionStatement)
	if !ok {
		t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
	}

	expectStr := "if (x < y) {return (x + 1); } else {return (y + 1); }"
	ifExpress, _ := express.Value.(*ast.IfExpression)
	if ifExpress.String() != expectStr {
		t.Errorf("expect if expression String() %q. got %q", expectStr, ifExpress.String())
	}
}

func TestFunctionExpression(t *testing.T) {
	input := `fn hello(x, y) { x = 1; return x + y; };`

	program := parseTestingProgram(t, input, 1)

	express, ok := program.Statements[0].(*ast.ExpressionStatement)
	if !ok {
		t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
	}

	expectStr := "fn hello (x, y) {x = 1; return (x + y); }"
	funExpress, ok := express.Value.(*ast.FunctionExpression)
	if funExpress.String() != expectStr {
		t.Errorf("expect function expression String() %q. got %q", expectStr, funExpress.String())
	}
}

func TestCallExpression(t *testing.T) {
	input := `hello(x, y) + a;`

	program := parseTestingProgram(t, input, 1)

	express, ok := program.Statements[0].(*ast.ExpressionStatement)
	if !ok {
		t.Errorf("statement not *ast.ExpressionStatement. got '%T'", program.Statements[0])
	}

	expectStr := "(hello(x, y) + a)"
	funExpress, ok := express.Value.(ast.Expression)
	if funExpress.String() != expectStr {
		t.Errorf("expect call expression String() %q. got %q", expectStr, funExpress.String())
	}
}

func testLiteralExpression(t *testing.T, expression ast.Expression, expectValue interface{}) bool {
	switch v := expectValue.(type) {
	case int:
		return testIntegerExpression(t, expression, int64(v))
	case int64:
		return testIntegerExpression(t, expression, v)
	case string:
		switch expression.(type) {
		case *ast.String:
			return testStringExpression(t, expression, v)
		case *ast.Identifier:
			return testIdentifierExpression(t, expression, v)
		}
	case bool:
		return testBooleanExpression(t, expression, v)
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

func testStringExpression(t *testing.T, expression ast.Expression, v string) bool {
	s, ok := expression.(*ast.String)
	if !ok {
		t.Errorf("expression is not *ast.String. got '%T'", expression)
		return false
	}

	if s.Value != v {
		t.Errorf("value for string expression is not %s. got '%s'", v, s.Value)
		return false
	}

	if s.TokenLieteral() != v {
		t.Errorf("token literal for string expression is not %s. got '%s'", v, s.TokenLieteral())
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

func testBooleanExpression(t *testing.T, expression ast.Expression, v bool) bool {
	ident, ok := expression.(*ast.Boolean)
	if !ok {
		t.Errorf("expression is not *ast.Boolean. got '%T'", expression)
		return false
	}

	if ident.Value != v {
		t.Errorf("value for boolean expression is not %t. got '%t'", v, ident.Value)
		return false
	}

	vInStr := strconv.FormatBool(v)
	if ident.TokenLieteral() != vInStr {
		t.Errorf("token literal for boolean expression is not %s. got '%s'", vInStr, ident.TokenLieteral())
		return false
	}

	return true
}

func parseTestingProgram(t *testing.T, input string, expectedStatementCount int) *ast.Program {
	lex := lexer.New(input)
	par := New(lex)

	program, err := par.ParseProgram()
	if err != nil {
		t.Fatalf("parse program for input: %q failed. error is: %q", input, err.Error())
		return nil
	}

	if len(program.Statements) != expectedStatementCount {
		t.Fatalf("program.Statements does not contain %d statements. got=%d", expectedStatementCount, len(program.Statements))
	}

	return program
}
