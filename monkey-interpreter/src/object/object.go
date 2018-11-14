package object

import (
	"ast"
	"bytes"
	"fmt"
	"strings"
)

type ObjectType string

const (
	INTEGER_OBJ  = "INTEGER"
	BOOLEAN_OBJ  = "BOOLEAN"
	STRING_OBJ   = "STRING"
	NULL_OBJ     = "NULL"
	RETURN_OBJ   = "RETURN_VALUE"
	ERROR_OBJ    = "ERROR"
	FUNCTION_OBJ = "FUNCTION"
)

type Object interface {
	Type() ObjectType
	Inspect() string
}

type Integer struct {
	Value int64
}

func (i *Integer) Type() ObjectType {
	return INTEGER_OBJ
}

func (i *Integer) Inspect() string {
	return fmt.Sprintf("%d", i.Value)
}

type Boolean struct {
	Value bool
}

func (b *Boolean) Type() ObjectType {
	return BOOLEAN_OBJ
}

func (b *Boolean) Inspect() string {
	return fmt.Sprintf("%t", b.Value)
}

type String struct {
	Value string
}

func (s *String) Type() ObjectType {
	return STRING_OBJ
}

func (s *String) Inspect() string {
	return fmt.Sprintf("%s", s.Value)
}

type Null struct {
}

func (n *Null) Type() ObjectType {
	return NULL_OBJ
}

func (n *Null) Inspect() string {
	return "null"
}

type ReturnValue struct {
	Value Object
}

func (r *ReturnValue) Type() ObjectType {
	return RETURN_OBJ
}

func (r *ReturnValue) Inspect() string {
	return fmt.Sprintf("return %s", r.Value.Inspect())
}

type Error struct {
	Msg string
}

func (e *Error) Type() ObjectType {
	return ERROR_OBJ
}

func (e *Error) Inspect() string {
	return fmt.Sprintf("error: %s", e.Msg)
}

type Function struct {
	Parameters []*ast.Identifier
	Body       *ast.BlockExpression
	Env        *Environment
}

func (f *Function) Type() ObjectType {
	return FUNCTION_OBJ
}

func (f *Function) Inspect() string {
	var buffer bytes.Buffer
	buffer.WriteString("fn (")

	params := []string{}
	for _, param := range f.Parameters {
		params = append(params, param.Value)
	}

	buffer.WriteString(strings.Join(params, ", "))
	buffer.WriteString(") ")
	buffer.WriteString(f.Body.String())

	return buffer.String()
}

func NewEnvironment() *Environment {
	return &Environment{storage: make(map[string]Object)}
}

func NewNestedEnvironment(outer *Environment) *Environment {
	env := NewEnvironment()
	env.outer = outer
	return env
}

type Environment struct {
	storage map[string]Object
	outer   *Environment
}

func (e *Environment) Set(key string, val Object) Object {
	e.storage[key] = val
	return val
}

func (e *Environment) Get(key string) (Object, bool) {
	val, ok := e.storage[key]
	if ok {
		return val, ok
	}

	return e.outer.Get(key)
}
