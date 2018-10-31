package ast

import (
	"bytes"
	"token"
)

type Node interface {
	TokenLieteral() string
	String() string
}

type Statement interface {
	Node
	statementNode()
}

type Expression interface {
	Node
	expressionNode()
}

type Program struct {
	Statements []Statement
}

func (p *Program) TokenLieteral() string {
	if len(p.Statements) > 0 {
		return p.Statements[0].TokenLieteral()
	}

	return ""
}

func (p *Program) String() string {
	var buffer bytes.Buffer
	for _, statement := range p.Statements {
		buffer.WriteString(statement.String())
		buffer.WriteString("\n")
	}

	return buffer.String()
}

type LetStatement struct {
	Token token.Token
	Name  *Identifier
	Value Expression
}

func (l *LetStatement) statementNode() {}

func (l *LetStatement) TokenLieteral() string {
	return l.Token.Literal
}

func (l *LetStatement) String() string {
	var buffer bytes.Buffer

	buffer.WriteString(l.Token.Literal)
	buffer.WriteString(" ")
	buffer.WriteString(l.Name.String())
	buffer.WriteString(" = ")
	buffer.WriteString(l.Value.String())
	buffer.WriteString(";")

	return buffer.String()
}

type Identifier struct {
	Token token.Token
	Value string
}

func (i *Identifier) statementNode() {}

func (i *Identifier) TokenLieteral() string {
	return i.Token.Literal
}

func (i *Identifier) String() string {
	return i.Token.Literal
}

type ReturnStatement struct {
	Token token.Token
	Value Expression
}

func (r *ReturnStatement) statementNode() {}

func (r *ReturnStatement) TokenLieteral() string {
	return r.Token.Literal
}

func (r *ReturnStatement) String() string {
	var buffer bytes.Buffer

	buffer.WriteString(r.Token.Literal)
	if r.Value != nil {
		buffer.WriteString(" ")
		buffer.WriteString(r.Value.String())
	}

	buffer.WriteString(";")

	return buffer.String()
}

type ExpressionStatement struct {
	Token token.Token
	Value Expression
}

func (es *ExpressionStatement) statementNode() {}

func (es *ExpressionStatement) TokenLieteral() string {
	return es.Token.Literal
}

func (ex *ExpressionStatement) String() string {
	return ""
}
