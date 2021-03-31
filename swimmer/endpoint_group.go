package swimmer

import (
	"fmt"
	"log"
	"os"
	"sync"
	"sync/atomic"
)

type SelfState uint8
type EndpointStatusType uint8

const (
	Init = iota
	Started
	Shutdown
)

const (
	AliveStateType = iota
	SuspectStateType
	DeadStateType
	LeftStateType
)

type Endpoint struct {
	Name    string
	Address []byte
	Status  EndpointStatusType
}

type EndpointGroup struct {
	self            *Endpoint
	selfState       SelfState
	group           []*Endpoint
	logger          *log.Logger
	transport       Transport
	inboundMsgChan  chan Message
	shutdownMark    int32
	shutdownChan    chan struct{}
	shutdownLock    sync.Mutex
	updateGroupLock sync.Mutex
}

func newEndpointGroup(config *Config) (*EndpointGroup, error) {
	if config.LogOutput != nil && config.Logger != nil {
		return nil, fmt.Errorf("cannot specify both LogOutput and Logger. Please choose a single log configuration setting")
	}

	logDest := config.LogOutput
	if logDest == nil {
		logDest = os.Stdout
	}

	logger := config.Logger
	if logger == nil {
		logger = log.New(logDest, "", log.LstdFlags)
	}

	inboundMessageChan := make(chan Message)

	config.transport.RegisterInboundChannel(inboundMessageChan)
	eg := &EndpointGroup{
		self:            config.Endpoint,
		selfState:       Init,
		group:           []*Endpoint{{config.Endpoint.Name, config.Endpoint.Address, AliveStateType}},
		logger:          logger,
		transport:       config.transport,
		inboundMsgChan:  inboundMessageChan,
		shutdownChan:    make(chan struct{}),
		shutdownLock:    sync.Mutex{},
		updateGroupLock: sync.Mutex{},
	}

	return eg, nil
}

func (e *EndpointGroup) addEndpointToGroup(endpoint *Endpoint) bool {
	e.updateGroupLock.Lock()
	defer e.updateGroupLock.Unlock()

	for _, e := range e.group {
		if e.Name == endpoint.Name {
			return false
		}
	}

	e.group = append(e.group, endpoint)
	return true
}

func (e *EndpointGroup) handleReceivedMessage(inboundMsg Message) {
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

		e.group = append(e.group, resp.from)

		joinResp := joinMessageResp{remoteEndpoints: e.group}
		buf, err := encodeMessage(joinRespMessageType, joinResp)
		if err != nil {
			e.logger.Printf("encode joinMessageType request failed, from: %s", inboundMsg.from)
			return
		}

		err = e.transport.SendMsg(inboundMsg.from, buf.Bytes())
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
	if !e.addEndpointToGroup(endpoint) {
		return nil
	}

	joinMsg := joinMessage{from: e.self, knownGroup: e.group}
	joinBuf, err := encodeMessage(joinMessageType, joinMsg)
	if err != nil {
		return err
	}


	if err := e.transport.SendMsg(endpoint, joinBuf.Bytes()); err != nil {
		return err
	}

	atomic.CompareAndSwapInt32(&(e.selfState.(uint8)), Init, Started)
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
