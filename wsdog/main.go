package main

import (
	"github.com/jessevdk/go-flags"
	"os"
)

type CommandLineArguments struct {
	ListenPort     uint16            `short:"l" long:"listen" description:"listen on port"`
	ConnectUrl     string            `short:"c" long:"connect" description:"connect to a WebSocket server"`
	Origin         string            `short:"o" long:"origin" description:"optional origin"`
	ExecuteCommand string            `short:"x" long:"execute" description:"execute command after connecting"`
	Wait           int64             `short:"w" long:"wait" default:"2" description:" wait given seconds after executing command"`
	Host           string            `long:"host" description:"optional host"`
	Subprotocol    string            `short:"s" long:"subprotocol" description:"optional subprotocol (default: )"`
	NoTlsCheck     bool              `short:"n" long:"no-check" description:"Do not check for unauthorized certificates"`
	Headers        map[string]string `short:"H" long:"header" description:"Set an HTTP header <header:value>. Repeat to set multiple like -H header1:value1 -H header2:value2. (--connect only)"`
	Auth           string            `long:"auth" description:"Add basic HTTP authentication header <username:password>. (--connect only)"`
	Ca             string            `long:"ca" description:"Specify a Certificate Authority (--connect only)"`
	Cert           string            `long:"cert" description:"Specify a Client SSL Certificate (--connect only)"`
	Key            string            `long:"key" description:"Specify a Client SSL Certificate's key (--connect only)"`
	Passphrase     string            `long:"passphrase" description:"Specify a Client SSL Certificate Key's passphrase (--connect only). If you don't provide a value, it will be prompted for."`
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
