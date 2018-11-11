package repl

import (
	"bufio"
	"evaluator"
	"fmt"
	"io"
	"lexer"
	"object"
	"parser"
)

const PROMPT = ">>"

func Start(in io.Reader, out io.Writer) {
	scanner := bufio.NewScanner(in)
	env := object.NewEnvironment()
	for {
		fmt.Printf(PROMPT)
		scanned := scanner.Scan()
		if !scanned {
			return
		}

		line := scanner.Text()
		lexer := lexer.New(line)
		parser := parser.New(lexer)
		program, err := parser.ParseProgram()
		if err != nil {
			fmt.Printf("parse program failed: %s", err)
		}

		obj := evaluator.Eval(program, env)
		if evaluator.IsError(obj) {
			fmt.Printf("evaluate program failed: %s", err)
		}

		fmt.Printf("%+v\n", obj.Inspect())
	}
}
