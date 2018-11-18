package token

type TokenType string

type Token struct {
	Type    TokenType
	Literal string
	Line    int
	Column  int
}

const (
	ILLEGAL = "ILLEGAL"
	EOF     = "EOF"

	// Identifiers
	IDENT  = "IDENT"
	INT    = "INT"
	STRING = "STRING"
	TRUE   = "TRUE"
	FALSE  = "FALSE"

	// Operators
	ASSIGN          = "="
	PLUS            = "+"
	PLUS_ASSIGN     = "+="
	PLUSPLUS        = "++"
	MINUS           = "-"
	MINUS_ASSIGN    = "-="
	MINUSMINUS      = "--"
	ASTERISK        = "*"
	ASTERISK_ASSIGN = "*="
	DIVIDE          = "/"
	DIVIDE_ASSIGN   = "/="
	REM             = "%"
	REM_ASSIGN      = "%="
	BANG            = "!"

	OR     = "|"
	AND    = "&"
	XOR    = "^"
	LSHIFT = "<<"
	RSHIFT = ">>"

	LOR  = "||"
	LAND = "&&"

	LT  = "<"
	LTE = "<="
	GT  = ">"
	GTE = ">="

	EQ    = "=="
	NOTEQ = "!="

	LBRACKET = "["
	RBRACKET = "]"

	// Delimiters
	COMMA     = ","
	SEMICOLON = ";"
	COLON     = ":"

	LPAREN = "("
	RPAREN = ")"
	LBRACE = "{"
	RBRACE = "}"

	// Keywords
	FUNCTION = "FUNCTION"
	LET      = "LET"
	IF       = "IF"
	ELSE     = "ELSE"
	RETURN   = "RETURN"
)

var keywords = map[string]TokenType{
	"fn":     FUNCTION,
	"let":    LET,
	"return": RETURN,
	"if":     IF,
	"else":   ELSE,
	"true":   TRUE,
	"false":  FALSE,
}

func LookupIdent(ident string) TokenType {
	if tok, ok := keywords[ident]; ok {
		return tok
	}

	return IDENT
}

// Precedence get precedence of the operator tokens
func (op Token) Precedence() int {
	switch op.Type {
	case LOR:
		return 1
	case LAND:
		return 2
	case EQ, NOTEQ:
		return 3
	case LT, LTE, GT, GTE:
		return 4
	case PLUS, MINUS, OR, XOR:
		return 5
	case ASTERISK, DIVIDE, REM, LSHIFT, RSHIFT, AND:
		return 6
	case LPAREN, PLUSPLUS, MINUSMINUS:
		return 7
	case LBRACKET:
		return 8
	}
	return 0
}
