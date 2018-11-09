package repl

import (
	"bufio"
	"evaluator"
	"fmt"
	"io"
	"lexer"
	"parser"
)

const PROMPT = ">>"

func Start(in io.Reader, out io.Writer) {
	scanner := bufio.NewScanner(in)
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

		obj, err := evaluator.Eval(program)
		if err != nil {
			fmt.Printf("evaluate program failed: %s", err)
		}

		fmt.Printf("%+v\n", obj.Inspect())
	}
}
