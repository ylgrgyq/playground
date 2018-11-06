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
		return evalStatements(node.Statements)
	case *ast.ExpressionStatement:
		return Eval(node.Value)
	case *ast.PrefixExpression:
		return evalPrefixExpression(node)
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

func evalStatements(statements []ast.Statement) (object.Object, error) {
	var result object.Object
	var err error
	for _, statement := range statements {
		result, err = Eval(statement)
		if err != nil {
			return nil, err
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

	return obj, nil
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
