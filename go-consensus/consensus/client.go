package consensus

import (
	"context"
	"log"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type StateMachine interface {
	ApplyOperation(index int64, op []byte) error
	GetCheckpoint()
	LoadCheckpoint()
}

func NewConsensus(callback StateMachine, configs *Configurations, rpcService RpcService, logger *log.Logger) *Consensus {
	applyLogChan := make(chan []*protos.LogEntry)
	node := NewNode(configs, rpcService, applyLogChan, logger)
	return &Consensus{
		callback: callback,
		node:     node,
		logger:   logger,
	}
}

type Consensus struct {
	done         chan struct{}
	callback     StateMachine
	applyLogChan chan []*protos.LogEntry
	node         *Node
	logger       *log.Logger
}

func (c *Consensus) Start() error {
	go func() {
		var appliedIndex int64 = 0
		for {
			select {
			case <-c.done:
				return
			case entries := <-c.applyLogChan:
				if len(entries) == 0 {
					continue
				}
				for _, entry := range entries {
					if entry.Index <= appliedIndex {
						continue
					}
					err := c.callback.ApplyOperation(entry.Index, entry.Data)
					if err != nil {
						c.logger.Printf("apply operation failed. index: %d", entry.Index)
						break
					}
					appliedIndex = entry.Index
				}

				c.node.UpdateAppliedLogIndex(appliedIndex)
			}
		}
	}()
	return c.node.Start()
}

func (c *Consensus) CommitOperation(op []byte) (*AppendResponse, error) {
	return c.node.Append(op)
}

func (c *Consensus) Stop(ctx context.Context) {
	c.node.Stop(ctx)
	close(c.done)
}
