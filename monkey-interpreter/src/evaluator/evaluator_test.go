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
	}

	for _, test := range tests {
		actual := evalTestingInput(t, test.input)
		if err := testCompareInteger(t, actual, test.expect); err != nil {
			t.Errorf("evaluate for input: %q failed. error is: %s", test.input, err)
		}
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
	}

	for _, test := range tests {
		actual := evalTestingInput(t, test.input)
		if err := testCompareBoolean(t, actual, test.expect); err != nil {
			t.Errorf("evaluate for input: %q failed. error is: %s", test.input, err)
		}
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
		var err error
		actual := evalTestingInput(t, test.input)
		switch v := test.expect.(type) {
		case int:
			err = testCompareInteger(t, actual, int64(v))
		case bool:
			err = testCompareBoolean(t, actual, v)
		default:
			err = testCompareNull(t, actual)
		}

		if err != nil {
			t.Errorf("evaluate for input: %q failed. error is: %s", test.input, err)
		}
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

	actual, err := Eval(program)
	if err != nil {
		t.Fatalf("evaluate program failed for input: %q. error is: %q", input, err.Error())
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
