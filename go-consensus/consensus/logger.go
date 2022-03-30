package consensus

import (
	"fmt"
	"github.com/fatih/color"
	"os"
	"sync"
	"time"
)

type Logger interface {
	Debug(v ...interface{})
	Debugf(format string, v ...interface{})

	Ok(v ...interface{})
	Okf(format string, v ...interface{})

	ReceiveMessage(v ...interface{})
	ReceiveMessagef(format string, v ...interface{})

	SendMessage(v ...interface{})

	Error(v ...interface{})
	Errorf(format string, v ...interface{})

	Fatal(v ...interface{})
	Fatalf(format string, v ...interface{})
}

func SetLogger(l Logger) {
	loggerMu.Lock()
	serverLogger = l
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
	loggerMu     sync.Mutex
	serverLogger = Logger(defaultLogger)
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
		printConsoleln(l.debugColor, fmt.Sprintf("DEBUG: %s", v...))
	}
}

func (l *DefaultLogger) Debugf(format string, v ...interface{}) {
	if l.debug {
		printConsoleln(l.debugColor, fmt.Sprintf("DEBUG: %s", fmt.Sprintf(format, v...)))
	}
}

func (l *DefaultLogger) ReceiveMessage(v ...interface{}) {
	printConsoleln(l.receiveColor, v...)
}

func (l *DefaultLogger) ReceiveMessagef(format string, v ...interface{}) {
	printConsolelnf(l.receiveColor, format, v...)
}

func (l *DefaultLogger) Ok(v ...interface{}) {
	printConsoleln(l.okColor, v...)
}

func (l *DefaultLogger) Okf(format string, v ...interface{}) {
	printConsolelnf(l.okColor, format, v...)
}

func (l *DefaultLogger) SendMessage(v ...interface{}) {
	printConsole(l.sendColor, v...)
}

func (l *DefaultLogger) Error(v ...interface{}) {
	printConsoleln(l.errorColor, v...)
}

func (l *DefaultLogger) Errorf(format string, v ...interface{}) {
	printConsolelnf(l.errorColor, format, v...)
}

func (l *DefaultLogger) Fatal(v ...interface{}) {
	printConsoleln(l.errorColor, v...)
	os.Exit(1)
}

func (l *DefaultLogger) Fatalf(format string, v ...interface{}) {
	printConsolelnf(l.errorColor, format, v...)
	os.Exit(1)
}

func printConsole(c *color.Color, v ...interface{}) {
	dt := time.Now()
	logStr := fmt.Sprintf("%s", v...)
	if _, err := c.Print(fmt.Sprintf("%s %s\n", dt.Format(time.RFC3339), logStr)); err != nil {
		panic(err)
	}
}

func printConsoleln(c *color.Color, v ...interface{}) {
	dt := time.Now()
	logStr := fmt.Sprintf("%s", v...)
	if _, err := c.Println(fmt.Sprintf("%s %s\n", dt.Format(time.RFC3339), logStr)); err != nil {
		panic(err)
	}
}

func printConsolelnf(c *color.Color, format string, v ...interface{}) {
	dt := time.Now()
	logStr := fmt.Sprintf(format, v...)
	if _, err := c.Printf(fmt.Sprintf("%s %s\n", dt.Format(time.RFC3339), logStr)); err != nil {
		panic(err)
	}
}
