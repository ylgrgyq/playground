package swimmer

type MessageType uint8

const (
	pingMessageType MessageType = iota
	pingAckMessageType
	joinMessageType
	joinResponseMessageType
	errorMessageType
)

type Message struct {
	From        Endpoint
	MessageType MessageType
	Payload     interface{}
}

type Join struct {
	KnownEndpoints []Endpoint
}

type JoinResponse struct {
	KnownEndpoints []Endpoint
}