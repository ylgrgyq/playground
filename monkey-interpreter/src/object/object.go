package object

import (
	"ast"
	"bytes"
	"fmt"
	"hash/fnv"
	"strings"
)

type ObjectType string

const (
	INTEGER_OBJ   = "INTEGER"
	BOOLEAN_OBJ   = "BOOLEAN"
	STRING_OBJ    = "STRING"
	NULL_OBJ      = "NULL"
	RETURN_OBJ    = "RETURN_VALUE"
	ERROR_OBJ     = "ERROR"
	FUNCTION_OBJ  = "FUNCTION"
	BUILTIN_OBJ   = "BUILTIN"
	ARRAY_OBJ     = "ARRAY"
	HASHTABLE_OBJ = "HASHTABLE"
)

type Object interface {
	Type() ObjectType
	Inspect() string
}

type Hashable interface {
	Hash() HashKey
}

type HashKey struct {
	Type  ObjectType
	Value uint64
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

func (i *Integer) Hash() HashKey {
	return HashKey{Type: INTEGER_OBJ, Value: uint64(i.Value)}
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

func (b *Boolean) Hash() HashKey {
	var v uint64
	if b.Value {
		v = 1
	} else {
		v = 0
	}

	return HashKey{Type: BOOLEAN_OBJ, Value: v}
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

func (s *String) Hash() HashKey {
	h := fnv.New64a()
	h.Write([]byte(s.Value))
	return HashKey{Type: STRING_OBJ, Value: h.Sum64()}
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

type Array struct {
	Elements []Object
}

func (a *Array) Type() ObjectType {
	return ARRAY_OBJ
}

func (a *Array) Inspect() string {
	var buffer bytes.Buffer
	buffer.WriteString("[")

	elems := []string{}
	for _, e := range a.Elements {
		elems = append(elems, e.Inspect())
	}

	buffer.WriteString(strings.Join(elems, ", "))
	buffer.WriteString("]")

	return buffer.String()
}

type HashPair struct {
	Key   Object
	Value Object
}
type HashTable struct {
	Pair map[HashKey]HashPair
}

func (h *HashTable) Type() ObjectType {
	return HASHTABLE_OBJ
}

func (h *HashTable) Inspect() string {
	var buffer bytes.Buffer
	buffer.WriteString("{")

	pairs := []string{}
	for _, v := range h.Pair {
		pairs = append(pairs, fmt.Sprintf("%s:%s", v.Key.Inspect(), v.Value.Inspect()))
	}

	buffer.WriteString(strings.Join(pairs, ", "))
	buffer.WriteString("}")

	return buffer.String()
}

type BuiltinFunction func(args ...Object) Object

type Builtin struct {
	Fn BuiltinFunction
}

func (f *Builtin) Type() ObjectType {
	return FUNCTION_OBJ
}

func (f *Builtin) Inspect() string {
	return "builtin function"
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

	if e.outer != nil {
		return e.outer.Get(key)
	}
	return nil, false
}
