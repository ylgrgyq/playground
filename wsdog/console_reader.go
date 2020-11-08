package main

import (
	"fmt"
	"github.com/chzyer/readline"
	"strings"
)

type ConsoleInputReader struct {
	outputChan chan string
	reader *readline.Instance
	done chan struct{}
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
	if err := c.reader.Close(); err != nil {
		wsdogLogger.Debugf("close input reader failed: %s", err.Error())
	}
}

func NewConsoleInputReader() *ConsoleInputReader {
	reader,err := readline.New("> ")
	if err != nil {
		wsdogLogger.Fatalf("setup read from console failed: %s", err)
	}

	outputChan := make(chan string)
	done := make(chan struct{})
	r := ConsoleInputReader{outputChan, reader, done}

	go func() {
		defer close(outputChan)
		defer close(done)
		for {
			text, err := reader.Readline()
			if err != nil {
				wsdogLogger.Debugf("receive error when read from console %s", err.Error())
				return
			}

			outputChan <- strings.TrimSuffix(text, "\n")
		}
	}()

	return &r
}