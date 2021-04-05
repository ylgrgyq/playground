package swimmer

import (
	"fmt"
	"log"
	"os"
	"sync"
	"sync/atomic"
)

type SelfState int32
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
	selfState       int32
	group           []*Endpoint
	logger          *log.Logger
	transport       Transport
	inboundMsgChan  chan InboundMessage
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

	eg := &EndpointGroup{
		self:            config.Endpoint,
		selfState:       Init,
		group:           []*Endpoint{{config.Endpoint.Name, config.Endpoint.Address, AliveStateType}},
		logger:          logger,
		transport:       config.transport,
		shutdownChan:    make(chan struct{}),
		updateGroupLock: sync.Mutex{},
	}

	return eg, nil
}

func (e *EndpointGroup) tryStartMainLoop() {
	if atomic.CompareAndSwapInt32(&e.selfState, Init, Started) {
		go e.mainLoop()
	}
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

func (e *EndpointGroup) mainLoop() {
	for {
		select {
		case inboundMsg := <-e.inboundMsgChan:
			if inboundMsg.Payload == nil {
				continue
			}
			e.handleReceivedMessage(inboundMsg)
		case <-e.shutdownChan:
			return
		}
	}
}

func (e *EndpointGroup) handleReceivedMessage(inboundMsg InboundMessage) {
	switch inboundMsg.MessageType {
	case joinResponseMessageType:
		resp, ok := inboundMsg.Payload.(JoinResponseMessage)
		if !ok {
			e.logger.Printf("receive invalid JoinResponseMessage, from: %s", inboundMsg.From.Name)
			return
		}

		for _, endpoint := range resp.KnownEndpoints {
			e.addEndpointToGroup(&endpoint)
		}
	case joinMessageType:
		resp, ok := inboundMsg.Payload.(JoinMessage)
		if !ok {
			e.logger.Printf("receive invalid join message, from: %s", inboundMsg.From.Name)
			return
		}

		joinResp := JoinResponseMessage{KnownEndpoints: e.GetEndpoints()}
		err := e.transport.SendMsg(&inboundMsg.From, joinMessageType, joinResp)
		if err != nil {
			e.logger.Printf("encode joinMessageType request failed, from: %s", inboundMsg.From)
			return
		}

		endpointsToAdd := append(resp.KnownEndpoints, inboundMsg.From)
		for _, endpoint := range endpointsToAdd {
			e.addEndpointToGroup(&endpoint)
		}
	}
}

func CreateEndpointGroup(config *Config) (*EndpointGroup, error) {
	eg, err := newEndpointGroup(config)
	if err != nil {
		return nil, err
	}
	return eg, nil
}

func (e *EndpointGroup) Join(endpoint *Endpoint) error {
	e.tryStartMainLoop()

	if !e.addEndpointToGroup(endpoint) {
		return nil
	}

	joinMsg := JoinMessage{KnownEndpoints: e.GetEndpoints()}
	if err := e.transport.SendMsg(endpoint, joinMessageType, joinMsg); err != nil {
		return err
	}

	return nil
}

func (e *EndpointGroup) NumOfKnownEndpoints() int {
	return len(e.group)
}

func (e *EndpointGroup) GetEndpoints() []Endpoint {
	eds := make([]Endpoint, len(e.group))
	for _, endpoint := range e.group {
		eds = append(eds, *endpoint)
	}
	return eds
}


func (e *EndpointGroup) Shutdown() error {
	if !atomic.CompareAndSwapInt32(&e.selfState, Started, Shutdown) {
		return nil
	}

	if err := e.transport.Shutdown(); err != nil {
		e.logger.Printf("[ERR] Failed to shutdownChan transport: %v", err)
	}

	close(e.shutdownChan)
	return nil
}
