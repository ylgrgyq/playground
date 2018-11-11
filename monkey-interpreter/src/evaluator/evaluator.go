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

func Eval(node ast.Node, env *object.Environment) object.Object {
	switch node := node.(type) {
	case *ast.Program:
		return evalProgram(node.Statements, env)
	case *ast.BlockExpression:
		return evalBlockStatements(node.Statements, env)
	case *ast.ExpressionStatement:
		return Eval(node.Value, env)
	case *ast.LetStatement:
		return evalLetStatement(node, env)
	case *ast.AssignStatement:
		return evalAssignStatement(node, env)
	case *ast.ReturnStatement:
		return evalReturnStatement(node, env)
	case *ast.PrefixExpression:
		return evalPrefixExpression(node, env)
	case *ast.InfixExpression:
		return evalInfixExpression(node, env)
	case *ast.IfExpression:
		return evalIfExpression(node, env)
	case *ast.CallExpression:
		return evalCallExpression(node, env)
	case *ast.Integer:
		return &object.Integer{Value: node.Value}
	case *ast.Boolean:
		return nativeBoolToBooleanObj(node.Value)
	case *ast.FunctionExpression:
		return evalFunctionExpression(node, env)
	case *ast.Identifier:
		val, ok := env.Get(node.Value)
		if ok {
			return val
		}

		return newError(fmt.Sprintf("unbind identifier: %s", val))
	default:
		return newError(fmt.Sprintf("unknown node type %T", node))
	}
}

func newError(msg string) *object.Error {
	return &object.Error{Msg: msg}
}

func IsError(obj object.Object) bool {
	return obj != nil && obj.Type() == object.ERROR_OBJ
}

func nativeBoolToBooleanObj(v bool) *object.Boolean {
	if v == true {
		return TRUE
	}

	return FALSE
}

func evalProgram(statements []ast.Statement, env *object.Environment) object.Object {
	var result object.Object
	for _, statement := range statements {
		result = Eval(statement, env)

		if IsError(result) {
			return result
		}

		if val, ok := result.(*object.ReturnValue); ok {
			return val.Value
		}
	}
	return result
}

func evalBlockStatements(statements []ast.Statement, env *object.Environment) object.Object {
	var result object.Object
	for _, statement := range statements {
		result = Eval(statement, env)
		if IsError(result) {
			return result
		}
		if result.Type() == object.RETURN_OBJ {
			return result
		}
	}
	return result
}

func evalLetStatement(node *ast.LetStatement, env *object.Environment) object.Object {
	val := Eval(node.Value, env)
	if IsError(val) {
		return val
	}

	env.Set(node.Name.Value, val)
	return val
}

func evalAssignStatement(node *ast.AssignStatement, env *object.Environment) object.Object {
	val := Eval(node.Value, env)
	if IsError(val) {
		return val
	}

	env.Set(node.Variable.Value, val)
	return val
}

func evalFunctionExpression(node *ast.FunctionExpression, env *object.Environment) object.Object {
	return &object.Function{Body: node.Body, Parameters: node.Parameters, Env: env}
}

func evalCallExpression(node *ast.CallExpression, env *object.Environment) object.Object {
	function := Eval(node.Function, env)
	if IsError(function) {
		return function
	}

	fn, ok := function.(*object.Function)
	if !ok {
		return newError(fmt.Sprintf("unknown function: %s", function.Inspect()))
	}

	newEnv := object.NewNestedEnvironment(fn.Env)

	for i, param := range fn.Parameters {
		arg := node.Arguments[i]
		p := Eval(arg, env)
		if IsError(p) {
			return p
		}

		newEnv.Set(param.Value, p)
	}

	ret := Eval(fn.Body, newEnv)

	if val, ok := ret.(*object.ReturnValue); ok {
		return val.Value
	}
	return ret
}

func evalPrefixExpression(node *ast.PrefixExpression, env *object.Environment) object.Object {
	var obj object.Object
	switch node.Operator {
	case "!":
		obj = Eval(node.Value, env)
		if IsError(obj) {
			return obj
		}
		return evalBangOperator(obj)
	case "-":
		obj = Eval(node.Value, env)
		if IsError(obj) {
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

func evalInfixExpression(node *ast.InfixExpression, env *object.Environment) object.Object {
	left := Eval(node.Left, env)
	if IsError(left) {
		return left
	}

	right := Eval(node.Right, env)
	if IsError(right) {
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

func evalIfExpression(node *ast.IfExpression, env *object.Environment) object.Object {
	con := Eval(node.Condition, env)
	if IsError(con) {
		return con
	}

	var ret object.Object
	ret = NULL
	if con != FALSE && con != NULL {
		ret = Eval(node.ThenBody, env)
	} else if node.ElseBody != nil {
		ret = Eval(node.ElseBody, env)
	}
	return ret
}

func evalReturnStatement(node *ast.ReturnStatement, env *object.Environment) object.Object {
	if node.Value != nil {
		ret := Eval(node.Value, env)
		if IsError(ret) {
			return ret
		}

		return &object.ReturnValue{Value: ret}
	}

	return &object.ReturnValue{Value: NULL}
}
