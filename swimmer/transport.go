package swimmer

type messageType uint8

type InboundMessageHandler interface {
	handleMessage(inboundMessage Message)
}

type Transport interface {
	SendMsg(target *Endpoint, msg []byte) error

	RegisterInboundMessageHandler(handler InboundMessageHandler)

	BlockShutdown() error
}

type CodecTransportWrapper struct {
	innerTransport Transport
}

func (c *CodecTransportWrapper) SendMessage(target *Endpoint, msg []byte) error {

}
