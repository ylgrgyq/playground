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

func Eval(node ast.Node) object.Object {
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
		return &object.Integer{Value: node.Value}
	case *ast.Boolean:
		return nativeBoolToBooleanObj(node.Value)
	default:
		return newError(fmt.Sprintf("unknown node type %T", node))
	}
}

func newError(msg string) *object.Error {
	return &object.Error{Msg: msg}
}

func isError(obj object.Object) bool {
	return obj != nil && obj.Type() == object.ERROR_OBJ
}

func nativeBoolToBooleanObj(v bool) *object.Boolean {
	if v == true {
		return TRUE
	}

	return FALSE
}

func evalProgram(statements []ast.Statement) object.Object {
	var result object.Object
	for _, statement := range statements {
		result = Eval(statement)

		if isError(result) {
			return result
		}

		if val, ok := result.(*object.ReturnValue); ok {
			return val.Value
		}
	}
	return result
}

func evalBlockStatements(statements []ast.Statement) object.Object {
	var result object.Object
	for _, statement := range statements {
		result = Eval(statement)
		if isError(result) {
			return result
		}
		if result.Type() == object.RETURN_OBJ {
			return result
		}
	}
	return result
}

func evalPrefixExpression(node *ast.PrefixExpression) object.Object {
	var obj object.Object
	switch node.Operator {
	case "!":
		obj = Eval(node.Value)
		if isError(obj) {
			return obj
		}
		return evalBangOperator(obj)
	case "-":
		obj = Eval(node.Value)
		if isError(obj) {
			return obj
		}

		return evalPrefixMinusOperator(obj)
	}

	return newError(fmt.Sprintf("unknown operator: %s%s", node.Operator, node.Value.String()))
}

func evalBangOperator(obj object.Object) object.Object {
	switch obj {
	case TRUE:
		return FALSE
	case FALSE:
		return TRUE
	case NULL:
		return TRUE
	default:
		return FALSE
	}
}

func evalPrefixMinusOperator(obj object.Object) object.Object {
	integer, ok := obj.(*object.Integer)
	if !ok {
		return newError(fmt.Sprintf("minus operator can not be used as prefix operator for %T", obj))
	}

	return &object.Integer{Value: -integer.Value}
}

func evalInfixExpression(node *ast.InfixExpression) object.Object {
	left := Eval(node.Left)
	if isError(left) {
		return left
	}

	right := Eval(node.Right)
	if isError(right) {
		return right
	}

	switch {
	case left.Type() == object.INTEGER_OBJ && right.Type() == object.INTEGER_OBJ:
		return evalIntegerInfixExpression(node.Operator, left, right)
	case node.Operator == "==":
		return nativeBoolToBooleanObj(left == right)
	case node.Operator == "!=":
		return nativeBoolToBooleanObj(left != right)
	}

	return newError(fmt.Sprintf("unknown operator: %s %s %s", node.Left.String(), node.Operator, node.Right.String()))
}

func evalIntegerInfixExpression(operator string, left object.Object, right object.Object) object.Object {
	leftInt := left.(*object.Integer)
	rightInt := right.(*object.Integer)
	switch operator {
	case "+":
		return &object.Integer{Value: leftInt.Value + rightInt.Value}
	case "-":
		return &object.Integer{Value: leftInt.Value - rightInt.Value}
	case "*":
		return &object.Integer{Value: leftInt.Value * rightInt.Value}
	case "/":
		return &object.Integer{Value: leftInt.Value / rightInt.Value}
	case "<":
		return &object.Boolean{Value: leftInt.Value < rightInt.Value}
	case ">":
		return &object.Boolean{Value: leftInt.Value > rightInt.Value}
	case "==":
		return nativeBoolToBooleanObj(leftInt.Value == rightInt.Value)
	case "!=":
		return nativeBoolToBooleanObj(leftInt.Value != rightInt.Value)
	default:
		return newError(fmt.Sprintf("unknown operator: %d %s %d", leftInt, operator, rightInt))
	}
}

func evalIfExpression(node *ast.IfExpression) object.Object {
	con := Eval(node.Condition)
	if isError(con) {
		return con
	}

	var ret object.Object
	ret = NULL
	if con != FALSE && con != NULL {
		ret = Eval(node.ThenBody)
	} else if node.ElseBody != nil {
		ret = Eval(node.ElseBody)
	}
	return ret
}

func evalReturnStatement(node *ast.ReturnStatement) object.Object {
	if node.Value != nil {
		ret := Eval(node.Value)
		if isError(ret) {
			return ret
		}

		return &object.ReturnValue{Value: ret}
	}

	return &object.ReturnValue{Value: NULL}
}
