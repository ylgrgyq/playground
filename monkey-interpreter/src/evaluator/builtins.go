package evaluator

import (
	"fmt"
	"object"
)

var builtins = map[string]*object.Builtin{
	"len": &object.Builtin{
		Fn: func(args ...object.Object) object.Object {
			if len(args) != 1 {
				return newError(fmt.Sprintf("wrong number of arguments. expect=%d, got=%d", 1, len(args)))
			}

			switch arg := args[0].(type) {
			case *object.String:
				return &object.Integer{Value: int64(len(arg.Value))}
			case *object.Array:
				return &object.Integer{Value: int64(len(arg.Elements))}
			default:
				return newError(fmt.Sprintf("argument to `len` not supported, got %s", args[0].Type()))
			}
		},
	},
	"first": &object.Builtin{
		Fn: func(args ...object.Object) object.Object {
			if len(args) != 1 {
				return newError(fmt.Sprintf("wrong number of arguments for function first. expect=%d, got=%d", 1, len(args)))
			}

			array, ok := args[0].(*object.Array)
			if !ok {
				return newError(fmt.Sprintf("wrong argument passed to function first. expect Array, got=%q", args[0].Type()))
			}

			length := len(array.Elements)
			if length > 0 {
				return array.Elements[0]
			}
			return NULL
		},
	},
	"last": &object.Builtin{
		Fn: func(args ...object.Object) object.Object {
			if len(args) != 1 {
				return newError(fmt.Sprintf("wrong number of arguments for function last. expect=%d, got=%d", 1, len(args)))
			}

			array, ok := args[0].(*object.Array)
			if !ok {
				return newError(fmt.Sprintf("wrong argument passed to function last. expect Array, got=%q", args[0].Type()))
			}

			length := len(array.Elements)
			if length > 0 {
				return array.Elements[len(array.Elements)-1]
			}
			return NULL
		},
	},
	"rest": &object.Builtin{
		Fn: func(args ...object.Object) object.Object {
			if len(args) != 1 {
				return newError(fmt.Sprintf("wrong number of arguments for function rest. expect=%d, got=%d", 1, len(args)))
			}

			array, ok := args[0].(*object.Array)
			if !ok {
				return newError(fmt.Sprintf("wrong argument passed to function rest. expect Array, got=%q", args[0].Type()))
			}

			length := len(array.Elements)
			if length > 0 {
				return &object.Array{Elements: array.Elements[1:]}
			}
			return &object.Array{}
		},
	},
	"push": &object.Builtin{
		Fn: func(args ...object.Object) object.Object {
			if len(args) != 2 {
				return newError(fmt.Sprintf("wrong number of arguments for function push. expect=%d, got=%d", 2, len(args)))
			}

			array, ok := args[0].(*object.Array)
			if !ok {
				return newError(fmt.Sprintf("wrong argument passed to function rest. expect Array, got=%q", args[0].Type()))
			}

			length := len(array.Elements)

			newArray := &object.Array{Elements: make([]object.Object, length+1, length+1)}
			copy(newArray.Elements, array.Elements)
			newArray.Elements[length] = args[1]

			return newArray
		},
	},
}
