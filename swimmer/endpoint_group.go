package swimmer

import (
	"fmt"
	"log"
	"os"
)

type Endpoint struct {
	Name    string
	Address []byte
}

type EndpointGroup struct {
	self      *Endpoint
	group     []*Endpoint
	logger    *log.Logger
	transport Transport
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

	eg := &EndpointGroup{
		self:      config.Endpoint,
		logger:    logger,
		transport: config.transport,
	}

	return eg, nil
}

func (e *EndpointGroup) handleReceivedMessage(msgType messageType, msg interface{}) {

}

func CreateEndpointGroup(config *Config) (*EndpointGroup, error) {
	eg, err := newEndpointGroup(config)
	if err != nil {
		return nil, err
	}
	return eg, nil
}

func (e *EndpointGroup) Join(endpoint *Endpoint) error {
	joinMsg := joinMessage{}
	joinBuf, err := encodeMessage(join, joinMsg)
	if err != nil {
		return err
	}

	if err := e.transport.sendMsg(endpoint, joinBuf.Bytes()); err != nil {
		return err
	}

	go func() {
		for {
			from, msgBuf := e.transport.readMsg()
			if len(msgBuf) == 0 {
				continue
			}

			msgType := msgBuf[0]
			switch messageType(msgType) {
			case join:
				var resp joinMessageResp
				err := decodePayload(msgBuf, &resp)
				if err != nil {

				}

				
			}
		}

	}()

	return nil
}
