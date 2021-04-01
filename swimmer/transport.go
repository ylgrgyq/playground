package swimmer

type messageType uint8

type Transport interface {
	SendMsg(target *Endpoint, msg []byte) error

	getInboundMessageChannel() chan InboundMessage

	Shutdown() error
}

type CodecTransportWrapper struct {
	innerTransport Transport
}

func (c *CodecTransportWrapper) SendMessage(target *Endpoint, msg []byte) error {

}
