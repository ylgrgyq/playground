package main

import (
	"fmt"
	"github.com/fatih/color"
	"os"
	"sync"
)

type Logger interface {
	Debug(v ...interface{})
	Debugf(format string, v ...interface{})

	Ok(v ...interface{})
	Okf(format string, v ...interface{})

	ReceiveMessage(v ...interface{})
	ReceiveMessagef(format string, v ...interface{})

	SendMessage(v ...interface{})
	SendMessagef(format string, v ...interface{})

	Error(v ...interface{})
	Errorf(format string, v ...interface{})

	Fatal(v ...interface{})
	Fatalf(format string, v ...interface{})
}

func SetLogger(l Logger) {
	loggerMu.Lock()
	wsdogLogger = l
	loggerMu.Unlock()
}

var (
	noColorLogger = &DefaultLogger{
		debugColor:   color.New(color.FgWhite),
		errorColor:   color.New(color.FgWhite),
		okColor:      color.New(color.FgWhite),
		receiveColor: color.New(color.FgWhite),
		sendColor:    color.New(color.FgWhite),
	}
	defaultLogger = &DefaultLogger{
		debugColor:   color.New(color.FgWhite),
		errorColor:   color.New(color.FgYellow),
		okColor:      color.New(color.FgGreen),
		receiveColor: color.New(color.FgBlue),
		sendColor:    color.New(color.FgWhite),
	}
	loggerMu    sync.Mutex
	wsdogLogger = Logger(defaultLogger)
)

// DefaultLogger is a default implementation of the Logger interface.
type DefaultLogger struct {
	debugColor   *color.Color
	errorColor   *color.Color
	okColor      *color.Color
	receiveColor *color.Color
	sendColor    *color.Color
	debug        bool
}

func (l *DefaultLogger) EnableDebug() {
	l.debug = true
}

func (l *DefaultLogger) Debug(v ...interface{}) {
	if l.debug {
		logln(l.debugColor, fmt.Sprintf("DEBUG: %s", v...))
	}
}

func (l *DefaultLogger) Debugf(format string, v ...interface{}) {
	if l.debug {
		logln(l.okColor, fmt.Sprintf("DEBUG: %s", fmt.Sprintf(format, v...)))
	}
}

func (l *DefaultLogger) ReceiveMessage(v ...interface{}) {
	logln(l.receiveColor, v...)
}

func (l *DefaultLogger) ReceiveMessagef(format string, v ...interface{}) {
	loglnf(l.receiveColor, format, v...)
}

func (l *DefaultLogger) Ok(v ...interface{}) {
	logln(l.okColor, v...)
}

func (l *DefaultLogger) Okf(format string, v ...interface{}) {
	loglnf(l.okColor, format, v...)
}

func (l *DefaultLogger) SendMessage(v ...interface{}) {
	logln(l.sendColor, v...)
}

func (l *DefaultLogger) SendMessagef(format string, v ...interface{}) {
	loglnf(l.sendColor, format, v...)
}

func (l *DefaultLogger) Error(v ...interface{}) {
	logln(l.errorColor, v...)
}

func (l *DefaultLogger) Errorf(format string, v ...interface{}) {
	loglnf(l.errorColor, format, v...)
}

func (l *DefaultLogger) Fatal(v ...interface{}) {
	logln(l.errorColor, v...)
	os.Exit(1)
}

func (l *DefaultLogger) Fatalf(format string, v ...interface{}) {
	loglnf(l.errorColor, format, v...)
	os.Exit(1)
}

func trailingNewLine(msg string) string {
	return fmt.Sprintf("%s\n", msg)
}

func logln(c *color.Color, v ...interface{}) {
	if _, err := c.Println(v...); err != nil {
		panic(err)
	}
}

func loglnf(c *color.Color, format string, v ...interface{}) {
	if _, err := c.Printf(trailingNewLine(fmt.Sprintf(format, v...))); err != nil {
		panic(err)
	}
}
