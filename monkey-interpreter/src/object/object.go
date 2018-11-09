package object

import (
	"fmt"
)

type ObjectType string

const (
	INTEGER_OBJ = "INTEGER"
	BOOLEAN_OBJ = "BOOLEAN"
	NULL_OBJ    = "NULL"
	RETURN_OBJ  = "RETURN_VALUE"
	ERROR_OBJ   = "ERROR"
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

func NewEnvironment() *Environment {
	return &Environment{storage: make(map[string]Object)}
}

type Environment struct {
	storage map[string]Object
}

func (e *Environment) Set(key string, val Object) Object {
	e.storage[key] = val
	return val
}

func (e *Environment) Get(key string) (Object, bool) {
	val, ok := e.storage[key]
	return val, ok
}
