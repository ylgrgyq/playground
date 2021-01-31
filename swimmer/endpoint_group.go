package swimmer

import (
	"fmt"
	"log"
	"os"
	"sync"
	"sync/atomic"
)

type EndpointStatusType uint8

const (
	AliveStateType = iota
	SuspectStateType
	DeadStateType
	LeftStateType
)

type Endpoint struct {
	Name    string
	Address []byte
}

type EndpointState struct {
	Endpoint
	Status EndpointStatusType
}

type EndpointGroup struct {
	self           *Endpoint
	group          []*EndpointState
	logger         *log.Logger
	transport      Transport
	inboundMsgChan chan InboundMessage
	shutdownMark   int32
	shutdownChan   chan struct{}
	shutdownLock   sync.Mutex
}

func newEndpointGroup(config *Config) (*EndpointGroup, error) {
	if config.LogOutput != nil && config.Logger != nil {
		return nil, fmt.Errorf("cannot specify both LogOutput and Logger. Please choose a single log configuration setting")
	}

	logDest := config.LogOutput
	if logDest == nil {
		logDest = os.Stderr
	}

	logger := config.Logger
	if logger == nil {
		logger = log.New(logDest, "", log.LstdFlags)
	}

	inboundMessageChan := make(chan InboundMessage)

	config.transport.registerInboundChannel(inboundMessageChan)
	eg := &EndpointGroup{
		self:           config.Endpoint,
		group:          []*EndpointState{{*config.Endpoint, AliveStateType}},
		logger:         logger,
		transport:      config.transport,
		inboundMsgChan: inboundMessageChan,
		shutdownChan:   make(chan struct{}),
	}

	return eg, nil
}

func (e *EndpointGroup) handleReceivedMessage(inboundMsg InboundMessage) {
	switch inboundMsg.messageType {
	case joinRespMessageType:
		var resp joinMessage
		err := decodePayload(inboundMsg.payload, &resp)
		if err != nil {
			e.logger.Printf("decode joinMessageType request failed, from: %s", inboundMsg.from)
			return
		}



	case joinMessageType:
		var resp joinMessage
		err := decodePayload(inboundMsg.payload, &resp)
		if err != nil {
			e.logger.Printf("decode joinMessageType request failed, from: %s", inboundMsg.from)
			return
		}

		e.group = append(e.group, resp.newEndpoint)

		joinResp := joinMessageResp{remoteEndpoints: e.group}
		buf, err := encodeMessage(joinRespMessageType, joinResp)
		if err != nil {
			e.logger.Printf("encode joinMessageType request failed, from: %s", inboundMsg.from)
			return
		}

		err = e.transport.sendMsg(inboundMsg.from, buf.Bytes())
		if err != nil {
			e.logger.Printf("encode joinMessageType request failed, from: %s", inboundMsg.from)
			return
		}


	}
}

func (e *EndpointGroup) mainLoop() {
	for {
		select {
		case <-e.shutdownChan:
			return
		case inboundMsg := <-e.inboundMsgChan:
			if len(inboundMsg.payload) == 0 {
				continue
			}
			e.handleReceivedMessage(inboundMsg)
		}
	}
}

func (e *EndpointGroup) hasShutdown() bool {
	return atomic.LoadInt32(&e.shutdownMark) == 1
}

func CreateEndpointGroup(config *Config) (*EndpointGroup, error) {
	eg, err := newEndpointGroup(config)
	if err != nil {
		return nil, err
	}
	return eg, nil
}

func (e *EndpointGroup) Join(endpoint *Endpoint) error {
	joinMsg := joinMessage{newEndpoint: &EndpointState{*e.self, AliveStateType}}
	joinBuf, err := encodeMessage(joinMessageType, joinMsg)
	if err != nil {
		return err
	}

	if err := e.transport.sendMsg(endpoint, joinBuf.Bytes()); err != nil {
		return err
	}

	go e.mainLoop()

	return nil
}

func (e *EndpointGroup) Shutdown() error {
	e.shutdownLock.Lock()
	defer e.shutdownLock.Unlock()

	if err := e.transport.BlockShutdown(); err != nil {
		e.logger.Printf("[ERR] Failed to shutdown transport: %v", err)
	}

	if !atomic.CompareAndSwapInt32(&e.shutdownMark, 0, 1) {
		return nil
	}

	close(e.shutdownChan)
	return nil
}
