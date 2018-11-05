package parser

import (
	"ast"
	"fmt"
	"lexer"
	"strconv"
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

var precedenceMap = map[token.TokenType]int{
	token.EQ:         EQUALS,
	token.NOTEQ:      EQUALS,
	token.LT:         LESSGREATER,
	token.GT:         LESSGREATER,
	token.PLUS:       SUM,
	token.MINUS:      SUM,
	token.DIVIDE:     PRODUCT,
	token.ASTERISK:   PRODUCT,
	token.PLUSPLUS:   CALL,
	token.MINUSMINUS: CALL,
	token.LPAREN:     CALL,
}

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
	p.registerPrefixParseFn(token.INT, p.parseInteger)
	p.registerPrefixParseFn(token.TRUE, p.parseBoolean)
	p.registerPrefixParseFn(token.FALSE, p.parseBoolean)
	p.registerPrefixParseFn(token.IF, p.parseIfExpression)
	p.registerPrefixParseFn(token.FUNCTION, p.parseFunction)

	p.registerPrefixParseFn(token.BANG, p.parsePrefix)
	p.registerPrefixParseFn(token.MINUS, p.parsePrefix)
	p.registerPrefixParseFn(token.LPAREN, p.parseGroupedExpression)
	p.registerPrefixParseFn(token.LBRACE, p.parseBlockExpression)

	p.registerInfixParseFn(token.MINUS, p.parseInfix)
	p.registerInfixParseFn(token.PLUS, p.parseInfix)
	p.registerInfixParseFn(token.DIVIDE, p.parseInfix)
	p.registerInfixParseFn(token.ASTERISK, p.parseInfix)
	p.registerInfixParseFn(token.EQ, p.parseInfix)
	p.registerInfixParseFn(token.NOTEQ, p.parseInfix)
	p.registerInfixParseFn(token.LT, p.parseInfix)
	p.registerInfixParseFn(token.GT, p.parseInfix)
	p.registerInfixParseFn(token.PLUSPLUS, p.parsePostfix)
	p.registerInfixParseFn(token.MINUSMINUS, p.parsePostfix)
	p.registerInfixParseFn(token.LPAREN, p.parseCallExpression)

	return &p
}

func (p *Parser) registerPrefixParseFn(tokenType token.TokenType, prefixFn prefixParseFn) {
	p.prefixFns[tokenType] = prefixFn
}

func (p *Parser) registerInfixParseFn(tokenType token.TokenType, infixFn infixParseFn) {
	p.infixFns[tokenType] = infixFn
}

func (p *Parser) Errors() []string {
	return p.errors
}

func (p *Parser) nextToken() token.Token {
	p.currentToken = p.peekToken
	p.peekToken = p.lex.NextToken()
	return p.currentToken
}

func (p *Parser) ParseProgram() (*ast.Program, error) {
	program := &ast.Program{}

	for !p.currentTokenTypeIs(token.EOF) {
		if statement, err := p.parseStatement(); err != nil {
			return nil, err
		} else if statement != nil {
			program.Statements = append(program.Statements, statement)
		}

		p.nextToken()
	}

	return program, nil
}

func (p *Parser) parseStatement() (ast.Statement, error) {
	var statement ast.Statement
	var err error
	switch p.currentToken.Type {
	case token.LET:
		statement, err = p.parseLetStatement()
	case token.RETURN:
		statement, err = p.parseReturnStatement()
	default:
		statement, err = p.parseExpressionStatement()
	}

	return statement, err
}

func (p *Parser) parseLetStatement() (*ast.LetStatement, error) {
	letStatement := &ast.LetStatement{Token: p.currentToken}

	if err := p.assertNextTokenType(token.IDENT); err != nil {
		return nil, err
	}

	letStatement.Name = &ast.Identifier{Token: p.currentToken, Value: p.currentToken.Literal}

	if err := p.assertNextTokenType(token.ASSIGN); err != nil {
		return nil, err
	}

	// skip ASSIGN token and point currentToken to the start of the next expression
	p.nextToken()
	letStatement.Value = p.parseExpression(LOWEST)

	if err := p.assertNextTokenType(token.SEMICOLON); err != nil {
		return nil, err
	}

	return letStatement, nil
}

func (p *Parser) parseReturnStatement() (*ast.ReturnStatement, error) {
	retStatement := &ast.ReturnStatement{Token: p.currentToken}

	if !p.nextTokenTypeIs(token.SEMICOLON) {
		p.nextToken()
		retStatement.Value = p.parseExpression(LOWEST)
	}

	if err := p.assertNextTokenType(token.SEMICOLON); err != nil {
		return nil, err
	}

	return retStatement, nil
}

func (p *Parser) parseExpressionStatement() (*ast.ExpressionStatement, error) {
	express := &ast.ExpressionStatement{Token: p.currentToken}

	express.Value = p.parseExpression(LOWEST)

	if err := p.assertNextTokenType(token.SEMICOLON); err != nil {
		return nil, err
	}

	return express, nil
}

/*
解析任意一个 expression 时都保证一个不变量，解析开始时 current token 指向 expression 开头，结束时 current token 指向 expression 末尾。
比如 if 表达式开始解析的时候要保证 current token 指向 if，结束时保证 current token 指向 }
*/
func (p *Parser) parseExpression(precedence int) ast.Expression {
	prefixFn := p.prefixFns[p.currentToken.Type]
	if prefixFn == nil {
		err := fmt.Sprintf("can not parse token type %s", p.currentToken.Type)
		p.errors = append(p.errors, err)
		return nil
	}

	left := prefixFn()
	// 最难的是这个 for 循环。当执行到这里时候 p.currentToken 指向一个 expression，peekToken 指向空或者下一个 operator
	// precedence 和 p.peekTokenPrecedence 比较实际比较的是 p.currentToken 指向的这个 expression 左右两边的 operator 优先级
	// 拿 A + B + C 来举例，当 p.currentToken 指向 B 时候，比较的就是 B 两侧 + 的优先级，当左边更高时就返回 left，并保持 p.peekToken 依然
	// 指向 B 右边的 + ，从而能在 B 作为 A + B 的 Right 结合为 Expression 整体时候，在上一层的 for 中继续从 p.peekToken 指向 B 右侧的 + 开始解析。
	// 拿 A + B * C 来举例，当 B 右侧优先级更高时，保持 B 去作为 left 和 B 后面的 Expression 结合
	for p.peekToken.Type != token.SEMICOLON && precedence < p.peekTokenPrecedence() {
		infixFn := p.infixFns[p.peekToken.Type]
		if infixFn == nil {
			break
		}

		p.nextToken()

		left = infixFn(left)
	}

	return left
}

func (p *Parser) nextTokenTypeIs(expect token.TokenType) bool {
	return p.peekToken.Type == expect
}

func (p *Parser) currentTokenTypeIs(expect token.TokenType) bool {
	return p.currentToken.Type == expect
}

func (p *Parser) assertNextTokenType(expect token.TokenType) error {
	return p.assertTokenType(expect, p.peekToken.Type)
}

func (p *Parser) assertCurrentTokenType(expect token.TokenType) error {
	return p.assertTokenType(expect, p.currentToken.Type)
}

func (p *Parser) assertTokenType(expect token.TokenType, actual token.TokenType) error {
	var err error
	if actual != expect {
		err = fmt.Errorf("expectd token type is %q, got %q", expect, actual)
	} else {
		p.nextToken()
	}

	return err
}

func (p *Parser) peekTokenTypeIs(expectType token.TokenType) bool {
	return p.peekToken.Type == expectType
}

func (p *Parser) parseIdentifier() ast.Expression {
	return &ast.Identifier{Token: p.currentToken, Value: p.currentToken.Literal}
}

func (p *Parser) parseInteger() ast.Expression {
	value, err := strconv.ParseInt(p.currentToken.Literal, 0, 64)
	if err != nil {
		msg := fmt.Sprintf("could not parse %q as intger", p.currentToken.Literal)
		p.errors = append(p.errors, msg)
		return nil
	}

	return &ast.Integer{Token: p.currentToken, Value: value}
}

func (p *Parser) parseBoolean() ast.Expression {
	var value bool
	if p.currentToken.Type == token.TRUE {
		value = true
	} else if p.currentToken.Type == token.FALSE {
		value = false
	} else {
		msg := fmt.Sprintf("could not parse %q as boolean", p.currentToken.Literal)
		p.errors = append(p.errors, msg)
		return nil
	}

	return &ast.Boolean{Token: p.currentToken, Value: value}
}

func (p *Parser) parsePrefix() ast.Expression {
	prefix := &ast.PrefixExpression{Token: p.currentToken, Operator: p.currentToken.Literal}

	precedence := p.currentTokenPrecedence()

	p.nextToken()

	prefix.Value = p.parseExpression(precedence)
	if prefix.Value == nil {
		return nil
	}

	return prefix
}

// when called p.currentToken must point to a infix operator
func (p *Parser) parseInfix(left ast.Expression) ast.Expression {
	infix := &ast.InfixExpression{Token: p.currentToken, Left: left, Operator: p.currentToken.Literal}

	precedence := p.currentTokenPrecedence()
	p.nextToken()

	// TODOs shall we seperate return a nil expression or parseExpression failed

	infix.Right = p.parseExpression(precedence)

	return infix
}

func (p *Parser) parsePostfix(left ast.Expression) ast.Expression {
	return &ast.PostfixExpression{Token: p.currentToken, Left: left, Operator: p.currentToken.Literal}
}

func (p *Parser) parseGroupedExpression() ast.Expression {
	p.nextToken()

	expression := p.parseExpression(LOWEST)

	if p.assertNextTokenType(token.RPAREN) == nil {
		return nil
	}

	return expression
}

func (p *Parser) parseIfExpression() ast.Expression {
	ifExpress := &ast.IfExpression{Token: p.currentToken}

	if p.assertNextTokenType(token.LPAREN) == nil {
		return nil
	}

	ifExpress.Condition = p.parseExpression(LOWEST)

	if p.assertCurrentTokenType(token.RPAREN) == nil {
		return nil
	}

	body := p.parseExpression(LOWEST)
	if body == nil {
		return nil
	}

	ifExpress.ThenBody = body.(*ast.BlockExpression)
	if p.peekToken.Type == token.ELSE {
		p.nextToken()

		if p.assertNextTokenType(token.LBRACE) == nil {
			return nil
		}

		body = p.parseExpression(LOWEST)
		if body == nil {
			return nil
		}
		ifExpress.ElseBody = body.(*ast.BlockExpression)
	}

	return ifExpress
}

func (p *Parser) parseBlockExpression() ast.Expression {
	block := &ast.BlockExpression{Token: p.currentToken}

	p.nextToken()
	for p.currentToken.Type != token.RBRACE {
		statement, _ := p.parseStatement()
		if statement != nil {
			block.Statements = append(block.Statements, statement)
		}

		p.nextToken()
	}

	return block
}

func (p *Parser) parseFunction() ast.Expression {
	function := &ast.FunctionExpression{Token: p.currentToken}

	if p.peekToken.Type == token.IDENT {
		p.nextToken()
		ident := p.parseIdentifier()
		function.Name = ident.(*ast.Identifier)
	}

	if p.assertNextTokenType(token.LPAREN) == nil {
		return nil
	}

	function.Parameters = p.parseParameters()

	if p.assertNextTokenType(token.LBRACE) == nil {
		return nil
	}

	body := p.parseBlockExpression()
	function.Body = body.(*ast.BlockExpression)
	return function
}

func (p *Parser) parseParameters() []*ast.Identifier {
	p.nextToken()
	params := []*ast.Identifier{}

	for p.currentToken.Type != token.RPAREN {
		params = append(params, &ast.Identifier{Token: p.currentToken, Value: p.currentToken.Literal})
		p.nextToken()
		if p.currentToken.Type == token.COMMA {
			p.nextToken()
		}
	}

	return params
}

func (p *Parser) parseCallExpression(fun ast.Expression) ast.Expression {
	call := &ast.CallExpression{Token: p.currentToken, Function: fun}

	if p.assertCurrentTokenType(token.LPAREN) == nil {
		return nil
	}

	args := []ast.Expression{}
	for p.currentToken.Type != token.RPAREN {
		if p.currentToken.Type != token.COMMA {
			args = append(args, p.parseExpression(LOWEST))
		}
		p.nextToken()
	}
	call.Arguments = args
	return call
}

func (p *Parser) peekTokenPrecedence() int {
	if precedence, ok := precedenceMap[p.peekToken.Type]; ok {
		return precedence
	}

	return LOWEST
}

func (p *Parser) currentTokenPrecedence() int {
	if precedence, ok := precedenceMap[p.currentToken.Type]; ok {
		return precedence
	}

	return LOWEST
}
