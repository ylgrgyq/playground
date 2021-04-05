package swimmer

import "fmt"

type MessageType uint8

type Transport interface {
	SendMsg(target *Endpoint, messageType MessageType, msg interface{}) error

	getInboundMessageChannel() chan InboundMessage

	Shutdown() error
}

type CodecTransportWrapper struct {
	innerTransport Transport
	shutdownChan   chan struct{}
}

func buildErrorInboundMessage(from Endpoint, errorMsg error) InboundMessage {
	return InboundMessage{
		From:        from,
		MessageType: errorMessageType,
		Payload:     errorMsg,
	}
}

func (c *CodecTransportWrapper) SendMessage(target *Endpoint, messageType MessageType, msg interface{}) error {
	msgInBytes, err := encodePayload(messageType, msg)
	if err != nil {
		return err
	}

	err = c.innerTransport.SendMsg(target, messageType, msgInBytes.Bytes())
	if err != nil {
		return err
	}

	return nil
}

func (c *CodecTransportWrapper) GetInboundMessageChannel() chan InboundMessage {
	decodedMsgChan := make(chan InboundMessage)
	innerChan := c.innerTransport.getInboundMessageChannel()
	go func() {
		for {
			select {
			case inboundMsg := <-innerChan:
				if inboundMsg.Payload == nil {
					continue
				}

				payload, ok := inboundMsg.Payload.([]byte)
				if !ok {
					decodedMsgChan <-
						buildErrorInboundMessage(
							inboundMsg.From,
							fmt.Errorf("receive inbount message with type: %T but neet byte array", inboundMsg.Payload),
						)
					continue
				}

				msg, err := decodePayload(inboundMsg.MessageType, payload)
				if err != nil {
					decodedMsgChan <-
						buildErrorInboundMessage(
							inboundMsg.From,
							fmt.Errorf("decode Payload failed: %v", err),
						)
					continue
				}

				decodedMsgChan <- InboundMessage{
					From:        inboundMsg.From,
					MessageType: inboundMsg.MessageType,
					Payload:     msg,
				}
			case <-c.shutdownChan:
				return
			}
		}
	}()

	return decodedMsgChan
}

func (c *CodecTransportWrapper) Shutdown() error {
	close(c.shutdownChan)
	return c.innerTransport.Shutdown()
}
