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
	case *ast.String:
		return &object.String{Value: node.Value}
	case *ast.Boolean:
		return nativeBoolToBooleanObj(node.Value)
	case *ast.ArrayLiteral:
		elems := []object.Object{}
		for _, ex := range node.Elements {
			elem := Eval(ex, env)
			if IsError(elem) {
				return elem
			}

			elems = append(elems, elem)
		}
		return &object.Array{Elements: elems}
	case *ast.HashLiteral:
		pairs := make(map[object.HashKey]object.HashPair)
		for kx, vx := range node.Pair {
			k := Eval(kx, env)
			if IsError(k) {
				return k
			}

			v := Eval(vx, env)
			if IsError(v) {
				return v
			}

			h, ok := k.(object.Hashable)
			if !ok {
				return newError(fmt.Sprintf("key type in HashLiteral is not Hashable. got %q", k.Type()))
			}
			pairs[h.Hash()] = object.HashPair{Key: k, Value: v}
		}
		return &object.HashTable{Pair: pairs}
	case *ast.FunctionExpression:
		return evalFunctionExpression(node, env)
	case *ast.Identifier:
		val, ok := env.Get(node.Value)
		if ok {
			return val
		}

		if builtin, ok := builtins[node.Value]; ok {
			return builtin
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

	params, err := evalArguments(node.Arguments, env)
	if err != nil {
		return err
	}

	switch fn := function.(type) {

	case *object.Function:
		newEnv := object.NewNestedEnvironment(fn.Env)

		for i, param := range fn.Parameters {
			p := params[i]
			newEnv.Set(param.Value, p)
		}

		ret := Eval(fn.Body, newEnv)

		if val, ok := ret.(*object.ReturnValue); ok {
			return val.Value
		}
		return ret
	case *object.Builtin:
		return fn.Fn(params...)
	default:
		return newError(fmt.Sprintf("unknown function: %s", function.Inspect()))
	}
}

func evalArguments(args []ast.Expression, env *object.Environment) ([]object.Object, object.Object) {
	var params []object.Object
	for _, arg := range args {
		p := Eval(arg, env)
		if IsError(p) {
			return nil, p
		}

		params = append(params, p)
	}
	return params, nil
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
	case node.Operator == "[":
		print("asdfasdf", node.TokenLieteral(), left.Inspect(), right.Inspect())
		return evalIndexExpression(left, right)
	case left.Type() == object.INTEGER_OBJ && right.Type() == object.INTEGER_OBJ:
		return evalIntegerInfixExpression(node.Operator, left, right)
	case left.Type() == object.STRING_OBJ && right.Type() == object.STRING_OBJ:
		return evalStringInfixExpression(node.Operator, left, right)
	case node.Operator == "==":
		return nativeBoolToBooleanObj(left == right)
	case node.Operator == "!=":
		return nativeBoolToBooleanObj(left != right)
	}

	return newError(fmt.Sprintf("unknown operator: %s %s %s", node.Left.String(), node.Operator, node.Right.String()))
}

func evalIndexExpression(left object.Object, right object.Object) object.Object {
	switch l := left.(type) {
	case *object.Array:
		index, ok := right.(*object.Integer)
		if !ok {
			newError(fmt.Sprintf("expect Integer for Array Index, got %q", right.Type()))
		}
		return l.Elements[index.Value]
	case *object.HashTable:
		h, ok := right.(object.Hashable)
		if !ok {
			return newError(fmt.Sprintf("expect Hashable for HashTable key, got %q", right.Type()))
		}
		v, ok := l.Pair[h.Hash()]
		if ok {
			return v.Value
		}
		return NULL
	default:
		return newError(fmt.Sprintf("unsupported type for index operator, got %q", left.Type()))
	}
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

func evalStringInfixExpression(operator string, left object.Object, right object.Object) object.Object {
	leftStr := left.(*object.String)
	rightStr := right.(*object.String)
	switch operator {
	case "+":
		return &object.String{Value: leftStr.Value + rightStr.Value}
	case "==":
		return nativeBoolToBooleanObj(leftStr.Value == rightStr.Value)
	case "!=":
		return nativeBoolToBooleanObj(leftStr.Value != rightStr.Value)
	default:
		return newError(fmt.Sprintf("unknown operator: %s %s %s", leftStr, operator, rightStr))
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
