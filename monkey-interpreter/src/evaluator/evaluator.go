package evaluator

import (
	"ast"
	"fmt"
	"object"
)

var (
	TRUE  = &object.Boolean{Value: true}
	FALSE = &object.Boolean{Value: false}
	NULL  = &object.Null{}
)

func Eval(node ast.Node) (object.Object, error) {
	switch node := node.(type) {
	case *ast.Program:
		return evalProgram(node.Statements)
	case *ast.BlockExpression:
		return evalBlockStatements(node.Statements)
	case *ast.ExpressionStatement:
		return Eval(node.Value)
	case *ast.PrefixExpression:
		return evalPrefixExpression(node)
	case *ast.InfixExpression:
		return evalInfixExpression(node)
	case *ast.IfExpression:
		return evalIfExpression(node)
	case *ast.ReturnStatement:
		return evalReturnStatement(node)
	case *ast.Integer:
		return &object.Integer{Value: node.Value}, nil
	case *ast.Boolean:
		v := nativeBoolToBooleanObj(node.Value)
		return v, nil
	default:
		return nil, fmt.Errorf("unknown node type %T", node)
	}
}

func nativeBoolToBooleanObj(v bool) *object.Boolean {
	if v == true {
		return TRUE
	}

	return FALSE
}

func evalProgram(statements []ast.Statement) (object.Object, error) {
	var result object.Object
	var err error
	for _, statement := range statements {
		result, err = Eval(statement)
		if err != nil {
			return nil, err
		}
		if val, ok := result.(*object.ReturnValue); ok {
			return val.Value, nil
		}
	}
	return result, nil
}

func evalBlockStatements(statements []ast.Statement) (object.Object, error) {
	var result object.Object
	var err error
	for _, statement := range statements {
		result, err = Eval(statement)
		if err != nil {
			return nil, err
		}
		if result.Type() == object.RETURN_OBJ {
			return result, nil
		}
	}
	return result, nil
}

func evalPrefixExpression(node *ast.PrefixExpression) (object.Object, error) {
	var obj object.Object
	var err error
	switch node.Operator {
	case "!":
		obj, err = Eval(node.Value)
		if err != nil {
			return nil, err
		}
		return evalBangOperator(obj)
	case "-":
		obj, err = Eval(node.Value)
		if err != nil {
			return nil, err
		}

		return evalPrefixMinusOperator(obj)
	}

	return NULL, nil
}

func evalBangOperator(obj object.Object) (object.Object, error) {
	switch obj {
	case TRUE:
		return FALSE, nil
	case FALSE:
		return TRUE, nil
	case NULL:
		return TRUE, nil
	default:
		return FALSE, nil
	}
}

func evalPrefixMinusOperator(obj object.Object) (object.Object, error) {
	integer, ok := obj.(*object.Integer)
	if !ok {
		return nil, fmt.Errorf("minus operator can not be used as prefix operator for %T", obj)
	}

	return &object.Integer{Value: -integer.Value}, nil
}

func evalInfixExpression(node *ast.InfixExpression) (object.Object, error) {
	left, err := Eval(node.Left)
	if err != nil {
		return nil, err
	}

	right, err := Eval(node.Right)
	if err != nil {
		return nil, err
	}

	switch {
	case left.Type() == object.INTEGER_OBJ && right.Type() == object.INTEGER_OBJ:
		return evalIntegerInfixExpression(node.Operator, left, right)
	case node.Operator == "==":
		return nativeBoolToBooleanObj(left == right), nil
	case node.Operator == "!=":
		return nativeBoolToBooleanObj(left != right), nil
	}

	return NULL, nil
}

func evalIntegerInfixExpression(operator string, left object.Object, right object.Object) (object.Object, error) {
	leftInt := left.(*object.Integer)
	rightInt := right.(*object.Integer)
	switch operator {
	case "+":
		return &object.Integer{Value: leftInt.Value + rightInt.Value}, nil
	case "-":
		return &object.Integer{Value: leftInt.Value - rightInt.Value}, nil
	case "*":
		return &object.Integer{Value: leftInt.Value * rightInt.Value}, nil
	case "/":
		return &object.Integer{Value: leftInt.Value / rightInt.Value}, nil
	case "<":
		return &object.Boolean{Value: leftInt.Value < rightInt.Value}, nil
	case ">":
		return &object.Boolean{Value: leftInt.Value > rightInt.Value}, nil
	case "==":
		return nativeBoolToBooleanObj(leftInt.Value == rightInt.Value), nil
	case "!=":
		return nativeBoolToBooleanObj(leftInt.Value != rightInt.Value), nil
	default:
		return NULL, nil
	}
}

func evalIfExpression(node *ast.IfExpression) (object.Object, error) {
	con, err := Eval(node.Condition)
	if err != nil {
		return nil, err
	}

	var ret object.Object
	ret = NULL
	if con != FALSE && con != NULL {
		ret, err = Eval(node.ThenBody)
	} else if node.ElseBody != nil {
		ret, err = Eval(node.ElseBody)
	}
	return ret, err
}

func evalReturnStatement(node *ast.ReturnStatement) (object.Object, error) {
	if node.Value != nil {
		ret, err := Eval(node.Value)
		if err != nil {
			return nil, err
		}
		return &object.ReturnValue{Value: ret}, nil
	}

	return NULL, nil
}
