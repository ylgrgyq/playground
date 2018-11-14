package ast

import (
	"bytes"
	"strings"
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

type AssignStatement struct {
	Token    token.Token
	Variable *Identifier
	Value    Expression
}

func (a *AssignStatement) statementNode() {}

func (a *AssignStatement) TokenLieteral() string {
	return a.Token.Literal
}

func (a *AssignStatement) String() string {
	var buffer bytes.Buffer
	buffer.WriteString(a.Variable.String())
	buffer.WriteString(" = ")
	buffer.WriteString(a.Value.String())
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
	return i.Value
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

type Boolean struct {
	Token token.Token
	Value bool
}

func (b *Boolean) expressionNode() {}

func (b *Boolean) TokenLieteral() string {
	return b.Token.Literal
}

func (b *Boolean) String() string {
	return b.Token.Literal
}

type String struct {
	Token token.Token
	Value string
}

func (s *String) expressionNode() {}

func (s *String) TokenLieteral() string {
	return s.Token.Literal
}

func (s *String) String() string {
	return s.Value
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

type PostfixExpression struct {
	Token    token.Token
	Left     Expression
	Operator string
}

func (p *PostfixExpression) expressionNode() {}

func (p *PostfixExpression) TokenLieteral() string {
	return p.Token.Literal
}

func (p *PostfixExpression) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("(")
	buffer.WriteString(p.Left.String())
	buffer.WriteString(p.Operator)
	buffer.WriteString(")")
	return buffer.String()
}

type BlockExpression struct {
	Token      token.Token
	Statements []Statement
}

func (b *BlockExpression) expressionNode() {}

func (b *BlockExpression) TokenLieteral() string {
	return b.Token.Literal
}

func (b *BlockExpression) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("{")
	for _, statement := range b.Statements {
		buffer.WriteString(statement.String())
		buffer.WriteString(" ")
	}

	buffer.WriteString("}")
	return buffer.String()
}

type IfExpression struct {
	Token     token.Token
	Condition Expression
	ThenBody  *BlockExpression
	ElseBody  *BlockExpression
}

func (i *IfExpression) expressionNode() {}

func (i *IfExpression) TokenLieteral() string {
	return i.Token.Literal
}

func (i *IfExpression) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("if ")
	buffer.WriteString(i.Condition.String())
	buffer.WriteString(" ")
	buffer.WriteString(i.ThenBody.String())
	if i.ElseBody != nil {
		buffer.WriteString(" else ")
		buffer.WriteString(i.ElseBody.String())
	}
	return buffer.String()
}

type FunctionExpression struct {
	Token      token.Token
	Name       *Identifier
	Parameters []*Identifier
	Body       *BlockExpression
}

func (f *FunctionExpression) expressionNode() {}

func (f *FunctionExpression) TokenLieteral() string {
	return f.Token.Literal
}

func (f *FunctionExpression) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("fn ")
	if f.Name != nil {
		buffer.WriteString(f.Name.String())
		buffer.WriteString(" ")
	}

	params := []string{}
	for _, param := range f.Parameters {
		params = append(params, param.String())
	}

	buffer.WriteString("(")
	buffer.WriteString(strings.Join(params, ", "))
	buffer.WriteString(") ")
	buffer.WriteString(f.Body.String())
	return buffer.String()
}

type CallExpression struct {
	Token     token.Token
	Function  Expression
	Arguments []Expression
}

func (c *CallExpression) expressionNode() {}

func (c *CallExpression) TokenLieteral() string {
	return c.Token.Literal
}

func (c *CallExpression) String() string {
	var buffer bytes.Buffer
	buffer.WriteString(c.Function.String())

	args := []string{}
	for _, arg := range c.Arguments {
		args = append(args, arg.String())
	}

	buffer.WriteString("(")
	buffer.WriteString(strings.Join(args, ", "))
	buffer.WriteString(")")

	return buffer.String()
}

type ArrayLiteral struct {
	Token    token.Token
	Elements []Expression
}

func (a *ArrayLiteral) expressionNode() {}

func (a *ArrayLiteral) TokenLieteral() string {
	return a.Token.Literal
}

func (a *ArrayLiteral) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("[")

	args := []string{}
	for _, arg := range a.Elements {
		args = append(args, arg.String())
	}
	buffer.WriteString(strings.Join(args, ", "))
	buffer.WriteString("]")

	return buffer.String()
}
