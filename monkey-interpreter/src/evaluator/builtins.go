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
			default:
				return newError(fmt.Sprintf("argument to `len` not supported, got %s", args[0].Type()))
			}
		},
	},
}
