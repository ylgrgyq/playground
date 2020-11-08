package main

import (
	"fmt"
	"github.com/chzyer/readline"
	"strings"
)

type ConsoleInputReader struct {
	outputChan chan string
	done bool
	reader *readline.Instance
}

// Move cursor to the first character of the line and erase the entire line
func (c *ConsoleInputReader) Clean() {
	fmt.Print("\r\u001b[2K\u001b[3D")
}

// Re-print the unfinished line to STDOUT with prompt.
func (c *ConsoleInputReader) Refresh() {
	c.reader.Refresh()
}

func (c *ConsoleInputReader) Close() {
	c.done = true
	_ = c.reader.Close()
	close(c.outputChan)
}

func NewConsoleInputReader() *ConsoleInputReader {
	reader,_ := readline.New("> ")

	outputChan := make(chan string)
	r := ConsoleInputReader{outputChan, false, reader}

	go func() {
		defer r.Close()
		for {
			if r.done {
				return
			}

			text, err := reader.Readline()
			if err != nil {
				wsdogLogger.Debugf("receive error when read from console %s", err)
				return
			}

			outputChan <- strings.TrimSuffix(text, "\n")
		}
	}()

	return &r
}