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
	BANG            = "!"

	LT = "<"
	GT = ">"

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
