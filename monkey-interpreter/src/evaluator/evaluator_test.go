package evaluator

import (
	"fmt"
	"lexer"
	"object"
	"parser"
	"testing"
)

func TestEvalIntegerValue(t *testing.T) {
	tests := []struct {
		input  string
		expect int64
	}{
		{"5;", 5},
		{"-5;", -5},
		{"-100;", -100},
		{"5 + 5;", 10},
		{"10 * 10 + 5", 105},
		{"10 + 5 * 10 + 1", 61},
		{"10 * (10 + 5)", 150},
		{"100 / (10 + 10);", 5},
		{"100 / 10 * -10 + 10;", -90},
		{"return 100 / 10 * -10 + 10;", -90},
	}

	for _, test := range tests {
		assertEvalResultEqual(t, test.input, test.expect)
	}
}

func TestEvalBooleanValue(t *testing.T) {
	tests := []struct {
		input  string
		expect bool
	}{
		{"true", true},
		{"false", false},
		{"!false", true},
		{"!true", false},
		{"!555", false},
		{"!!555", true},
		{"!!!555", false},
		{"10 < 5", false},
		{"10 > 5", true},
		{"10 != 5", true},
		{"10 == 5", false},
		{"true == true", true},
		{"true != true", false},
		{"true == false", false},
		{"1 + 5 == 7 - 1", true},
		{"1 + 5 == 8 - 1", false},
		{"\"haha\" == \"haha\"", true},
		{"\"haha\" == \"hoho\"", false},
	}

	for _, test := range tests {
		assertEvalResultEqual(t, test.input, test.expect)
	}
}

func TestEvalStringValue(t *testing.T) {
	tests := []struct {
		input  string
		expect string
	}{
		{`"niuniu"`, "niuniu"},
		{`"hello"`, "hello"},
		{`"a\t\'\"\\"`, "a\t'\"\\"},
		{`"a\t\'\"\\" + " nihao"`, "a\t'\"\\ nihao"},
	}

	for _, test := range tests {
		assertEvalResultEqual(t, test.input, test.expect)
	}
}

func TestIfElseExpression(t *testing.T) {
	tests := []struct {
		input  string
		expect interface{}
	}{
		{"if (true) {1} else {2}", 1},
		{"if (false) {1} else {2}", 2},
		{"if (1 + 1 != 6) {1 + 2} else {3 + 4}", 3},
		{"if (2 + 3 == 5) {5}", 5},
		{"if (2 + 3 != 5) {5}", NULL},
	}

	for _, test := range tests {
		assertEvalResultEqual(t, test.input, test.expect)
	}
}

func TestReturnStatement(t *testing.T) {
	tests := []struct {
		input  string
		expect interface{}
	}{
		{"return 1 + 2 + 3", 6},
		{"return \"hello\"", "hello"},
		{"return;", NULL},
		{"if (true){ 1 + 1; return 3;}", 3},
		{`if (true)
			{ if (false)
				{return 1 + 1;}
			  else { return 3;}}`, 3},
		{`if (true)
			  { if (true)
				  {return 1 + 1;}
				return 6;}`, 2},
		{`if (true) { if (true) {return;} return 6;}`, NULL},
	}

	for _, test := range tests {
		assertEvalResultEqual(t, test.input, test.expect)
	}
}

func TestEvalError(t *testing.T) {
	tests := []struct {
		input  string
		expect string
	}{
		{"true + 5", "unknown operator: true + 5"},
		{"true - true", "unknown operator: true - true"},
		{"return true - true", "unknown operator: true - true"},
	}

	for _, test := range tests {
		lexer := lexer.New(test.input)
		parser := parser.New(lexer)

		program, err := parser.ParseProgram()
		if err != nil {
			t.Fatalf("parse program for input: %q failed. error is: %q", test.input, err.Error())
		}

		actual := Eval(program, object.NewEnvironment())
		if !IsError(actual) {
			t.Errorf("need an error for input: %s. but got %T", test.input, actual)
		}

		error := actual.(*object.Error)
		if error.Msg != test.expect {
			t.Errorf("expect error msg: %s for input %s. but got %s", test.expect, test.input, error.Msg)
		}
	}
}

func TestLetStatement(t *testing.T) {
	tests := []struct {
		input  string
		expect interface{}
	}{
		{"let x = 1; return x + x + 3", 5},
		{"let x = 1; x", 1},
		{"let x = 1; let y = 100; if (x < y) {return x + y}", 101},
		{"let x = 1; let y = 100; if (x < y) {x = y; y = 2; return x + y}", 102},
		{"let identity = fn(x) {x}; identity(100);", 100},
		{"let add = fn(x, y) {x + y}; add(5, 10);", 15},
		{"let add = fn(x, y) {x + y}; add(5, add(5,5));", 15},
		{`let adder = fn (x) {return fn(y) {return x + y;}}; 
		  let add_one = adder(1); 
		  add_one(10);`,
			11},
	}

	for _, test := range tests {
		assertEvalResultEqual(t, test.input, test.expect)
	}
}

func assertEvalResultEqual(t *testing.T, input string, expect interface{}) {
	var err error
	actual := evalTestingInput(t, input)
	switch v := expect.(type) {
	case int:
		err = testCompareInteger(t, actual, int64(v))
	case int64:
		err = testCompareInteger(t, actual, v)
	case bool:
		err = testCompareBoolean(t, actual, v)
	case string:
		err = testCompareString(t, actual, v)
	default:
		err = testCompareNull(t, actual)
	}

	if err != nil {
		t.Errorf("evaluate for input: %q failed. error is: %s", input, err)
	}
}

func testCompareNull(t *testing.T, obj object.Object) error {
	if obj == NULL {
		return nil
	}

	return fmt.Errorf("evaluated object is not Null. got %T", obj)
}

func evalTestingInput(t *testing.T, input string) object.Object {
	lexer := lexer.New(input)
	parser := parser.New(lexer)

	program, err := parser.ParseProgram()
	if err != nil {
		t.Fatalf("parse program for input: %q failed. error is: %q", input, err.Error())
	}

	actual := Eval(program, object.NewEnvironment())
	if IsError(actual) {
		t.Fatalf("evaluate program failed for input: %q. error is: %q", input, actual.(*object.Error).Msg)
	}

	return actual
}

func testCompareInteger(t *testing.T, actual object.Object, expect int64) error {
	integer, ok := actual.(*object.Integer)
	if !ok {
		return fmt.Errorf("evaluated object is not integer. got %T", actual)
	}

	if integer.Value != expect {
		return fmt.Errorf("evaluated object is not %d. got %d", expect, integer.Value)
	}

	if integer.Inspect() != fmt.Sprintf("%d", expect) {
		return fmt.Errorf("Inspect() for integer object is not %d. got %q", expect, integer.Inspect())
	}

	return nil
}

func testCompareBoolean(t *testing.T, actual object.Object, expect bool) error {
	boolean, ok := actual.(*object.Boolean)
	if !ok {
		return fmt.Errorf("evaluated object is not bool. got %T", actual)
	}

	if boolean.Value != expect {
		return fmt.Errorf("evaluated object is not %t. got %t", expect, boolean.Value)
	}

	if boolean.Inspect() != fmt.Sprintf("%t", expect) {
		return fmt.Errorf("Inspect() for boolean object is not %t. got %q", expect, boolean.Inspect())
	}

	return nil
}

func testCompareString(t *testing.T, actual object.Object, expect string) error {
	s, ok := actual.(*object.String)
	if !ok {
		return fmt.Errorf("evaluated object is not string. got %T", actual)
	}

	if s.Value != expect {
		return fmt.Errorf("evaluated object is not %s. got %s", expect, s.Value)
	}

	if s.Inspect() != fmt.Sprintf("%s", expect) {
		return fmt.Errorf("Inspect() for string object is not %s. got %q", expect, s.Inspect())
	}

	return nil
}
