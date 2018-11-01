package parser

import (
	"ast"
	"fmt"
	"lexer"
	"token"
)

const (
	_ int = iota
	LOWEST
	EQUALS
	LESSGREATER
	SUM
	PRODUCT
	PREFIX
	CALL
)

type (
	prefixParseFn func() ast.Expression
	infixParseFn  func(ast.Expression) ast.Expression
)

type Parser struct {
	lex *lexer.Lexer

	currentToken token.Token
	peekToken    token.Token
	errors       []string

	prefixFns map[token.TokenType]prefixParseFn
	infixFns  map[token.TokenType]infixParseFn
}

func New(l *lexer.Lexer) *Parser {
	p := Parser{lex: l,
		prefixFns: make(map[token.TokenType]prefixParseFn),
		infixFns:  make(map[token.TokenType]infixParseFn)}
	p.nextToken()
	p.nextToken()

	p.registerPrefixParseFn(token.IDENT, p.parseIdentifier)

	return &p
}

func (p *Parser) registerPrefixParseFn(tokenType token.TokenType, prefixFn prefixParseFn) {
	p.prefixFns[tokenType] = prefixFn
}

func (p *Parser) registerInfixParseFn(tokenType token.TokenType, infixFn infixParseFn) {
	p.infixFns[tokenType] = infixFn
}

func (p *Parser) parsePrefixExpression() *ast.Expression {

	return nil
}

func (p *Parser) Errors() []string {
	return p.errors
}

func (p *Parser) expectTokenTypeError(expect token.TokenType) {
	err := fmt.Sprintf("expectd token type is %s, got %s", expect, p.peekToken.Type)
	p.errors = append(p.errors, err)
}

func (p *Parser) nextToken() token.Token {
	p.currentToken = p.peekToken
	p.peekToken = p.lex.NextToken()
	return p.currentToken
}

func (p *Parser) ParseProgram() *ast.Program {
	program := &ast.Program{}

	for p.currentToken.Type != token.EOF {
		var statement ast.Statement
		switch p.currentToken.Type {
		case token.LET:
			statement = p.parseLetStatement()
		case token.RETURN:
			statement = p.parseReturnStatement()
		default:
			statement = p.parseExpressionStatement()
		}

		if statement != nil {
			program.Statements = append(program.Statements, statement)
		}

		p.nextToken()
	}

	return program
}

func (p *Parser) parseLetStatement() *ast.LetStatement {
	letStatement := &ast.LetStatement{Token: p.currentToken}

	var expect *token.Token

	expect = p.expectNextTokenType(token.IDENT)
	if expect == nil {
		return nil
	}

	letStatement.Name = &ast.Identifier{Token: *expect, Value: expect.Literal}

	expect = p.expectNextTokenType(token.ASSIGN)
	if expect == nil {
		return nil
	}

	for p.currentToken.Type != token.SEMICOLON {
		p.nextToken()
	}

	return letStatement
}

func (p *Parser) parseReturnStatement() *ast.ReturnStatement {
	retStatement := &ast.ReturnStatement{Token: p.currentToken}

	for p.currentToken.Type != token.SEMICOLON {
		p.nextToken()
	}

	return retStatement
}

func (p *Parser) parseExpressionStatement() *ast.ExpressionStatement {
	express := &ast.ExpressionStatement{Token: p.currentToken}

	express.Value = p.parseExpression(LOWEST)

	if p.peekToken.Type == token.SEMICOLON {
		p.nextToken()
	}

	return express
}

func (p *Parser) parseExpression(precedence int) ast.Expression {
	prefixFn := p.prefixFns[p.currentToken.Type]
	if prefixFn == nil {
		err := fmt.Sprintf("can not parse token type %s", p.currentToken.Type)
		p.errors = append(p.errors, err)
		return nil
	}

	left := prefixFn()

	return left
}

func (p *Parser) expectNextTokenType(expect token.TokenType) *token.Token {
	var peekToken token.Token
	if p.peekToken.Type == expect {
		peekToken = p.peekToken
	} else {
		p.expectTokenTypeError(expect)
	}

	p.nextToken()
	return &peekToken
}

func (p *Parser) parseIdentifier() ast.Expression {
	return &ast.Identifier{Token: p.currentToken, Value: p.currentToken.Literal}
}
