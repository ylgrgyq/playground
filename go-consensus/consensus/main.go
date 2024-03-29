package consensus

import (
	"context"
	"log"
	"os"

	"github.com/jessevdk/go-flags"
)

type ApplicationOptions struct {
	ConfigurationFilePath string `short:"c" long:"config" description:"path to configuration file"`
	EnableDebug           bool   `long:"debug" description:"enable debug log"`
}

type CommandLineOptions struct {
	ApplicationOptions
}

func parseCommandLineArguments() CommandLineOptions {
	var appOpts ApplicationOptions
	parser := flags.NewParser(&appOpts, flags.Default)

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

	if appOpts.ConfigurationFilePath == "" {
		parser.WriteHelp(os.Stderr)
		os.Exit(1)
	}

	return CommandLineOptions{appOpts}
}

type TestingStateMachine struct {
	logger *log.Logger
}

func (t *TestingStateMachine) ApplyOperation(index int64, op []byte) error {
	t.logger.Printf("apply operation index: %d, op len: %d", index, len(op))
	return nil
}

func (t *TestingStateMachine) GetCheckpoint() (int64, []byte, error) {
	return 0, nil, nil
}

func (t *TestingStateMachine) LoadCheckpoint() {

}

func Main() {
	logger := log.New(log.Writer(), "[Main]", log.LstdFlags|log.Lshortfile|log.Lmsgprefix|log.Lmicroseconds)
	cliOpts := parseCommandLineArguments()

	config, err := ParseConfig(cliOpts.ConfigurationFilePath)
	if err != nil {
		logger.Fatalf("parse config failed: %s", err)
	}

	logger.Printf("%s", config)

	rpcService, err := NewRpcService(logger, config)
	if err != nil {
		logger.Fatalf("create rpc service failed: %s", err)
	}

	go func() {
		if err := rpcService.Start(); err != nil {
			logger.Fatalf("start rpc service failed: %s", err)
		}
	}()

	defer func() {
		if err := rpcService.Shutdown(context.Background()); err != nil {
			logger.Fatalf("shutdown rpc service failed: %s", err)
		}
	}()

	stateMachine := TestingStateMachine{logger}
	consensus := NewConsensus(&stateMachine, config, rpcService, logger)

	if err = consensus.Start(); err != nil {
		logger.Fatalf("start node failed: %s", err)
	}

	for {
		select {
		case <-consensus.Done:

		}
	}
}
