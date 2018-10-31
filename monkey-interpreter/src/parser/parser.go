package parser

import (
	"ast"
	"fmt"
	"lexer"
	"token"
)

type Parser struct {
	lex *lexer.Lexer

	currentToken token.Token
	peekToken    token.Token
	errors       []string
}

func New(l *lexer.Lexer) *Parser {
	p := Parser{lex: l}
	p.nextToken()
	p.nextToken()
	return &p
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
