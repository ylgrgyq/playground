package swimmer

type MessageTransport interface {
	SendMessage(target *Endpoint, message Message) error

	GetInboundMessageChannel() chan Message

	Shutdown() error
}

