package main

import (
	"bufio"
	"os"
)

type ConsoleInputReader struct {
	outputChan chan string
	done bool
}

func (c *ConsoleInputReader) close() {
	c.done = true
}

func newConsoleInputReader() *ConsoleInputReader {
	outputChan := make(chan string)

	r := ConsoleInputReader{outputChan, false}

	go func() {
		ioReader := bufio.NewReader(os.Stdin)
		for {
			if r.done {
				return
			}

			wsdogLogger.Infof("> ")
			text, err := ioReader.ReadString('\n')
			if err != nil {
				panic(err)
			}

			outputChan <- text
		}
	}()

	return &r
}