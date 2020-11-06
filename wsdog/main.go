package main

import (
	"github.com/jessevdk/go-flags"
	"os"
)

type CommandLineArguments struct {
	ListenPort     uint16            `short:"l" long:"listen" description:"listen on port"`
	ConnectUrl     string            `short:"c" long:"connect" description:"connect to a WebSocket server"`
	Origin         string            `short:"o" long:"origin" description:"optional origin"`
	ExecuteCommand string            `short:"x" long:"execute" descritpion:"execute command after connecting"`
	Host           string            `long:"host" description:"optional host"`
	Subprotocol    string            `short:"s" long:"subprotocol" description:"optional subprotocol (default: )"`
	Headers        map[string]string `short:"H" long:"header" description:"Set an HTTP header <header:value>. Repeat to set multiple. (--connect only) (default: )"`
	Auth           string            `long:"auth" description:"Add basic HTTP authentication header <username:password>. (--connect only)"`
	EnableDebug    bool              `long:"debug" description:"enable debug log"`
	EnableSlash    bool              `long:"slash" description:"Enable slash commands for control frames (/ping, /pong, /close [code [, reason]])"`
	ShowPingPong   bool              `short:"P" long:"show-ping-pong" description:"print a notification when a ping or pong is received"`
}

func parseCommandLineArguments() CommandLineArguments {
	var cliOpts CommandLineArguments
	parser := flags.NewParser(&cliOpts, flags.Default)
	if _, err := parser.Parse(); err != nil {
		switch flagsErr := err.(type) {
		case *flags.Error:
			if flagsErr.Type == flags.ErrHelp {
				os.Exit(0)
			}

			parser.WriteHelp(os.Stderr)
			os.Exit(1)
		default:
			parser.WriteHelp(os.Stderr)
			os.Exit(1)
		}
	}

	if cliOpts.ConnectUrl == "" && cliOpts.ListenPort == 0 {
		parser.WriteHelp(os.Stderr)
		os.Exit(1)
	}

	if cliOpts.EnableDebug {
		defaultLogger.EnableDebug()
	}

	return cliOpts
}

func main() {
	var cliOpts = parseCommandLineArguments()

	if cliOpts.ConnectUrl != "" {
		runAsClient(cliOpts)
	} else {
		runAsServer(cliOpts)
	}
}
