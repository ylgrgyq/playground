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
	var buffer bytes.Buffer
	buffer.WriteString(ex.Value.String())
	buffer.WriteString(";")
	return buffer.String()
}

type Identifier struct {
	Token token.Token
	Value string
}

func (i *Identifier) expressionNode() {}

func (i *Identifier) TokenLieteral() string {
	return i.Token.Literal
}

func (i *Identifier) String() string {
	return i.Token.Literal
}

type Integer struct {
	Token token.Token
	Value int64
}

func (i *Integer) expressionNode() {}

func (i *Integer) TokenLieteral() string {
	return i.Token.Literal
}

func (i *Integer) String() string {
	return i.Token.Literal
}

type PrefixExpression struct {
	Token    token.Token
	Operator string
	Value    Expression
}

func (p *PrefixExpression) expressionNode() {}

func (p *PrefixExpression) TokenLieteral() string {
	return p.Token.Literal
}

func (p *PrefixExpression) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("(")
	buffer.WriteString(p.Operator)
	buffer.WriteString(p.Value.String())
	buffer.WriteString(")")
	return buffer.String()
}

type InfixExpression struct {
	Token    token.Token
	Left     Expression
	Operator string
	Right    Expression
}

func (i *InfixExpression) expressionNode() {}

func (i *InfixExpression) TokenLieteral() string {
	return i.Token.Literal
}

func (i *InfixExpression) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("(")
	buffer.WriteString(i.Left.String())
	buffer.WriteString(" ")
	buffer.WriteString(i.Operator)
	buffer.WriteString(" ")
	buffer.WriteString(i.Right.String())
	buffer.WriteString(")")
	return buffer.String()
}
