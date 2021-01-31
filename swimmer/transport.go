package swimmer

type messageType uint8

type Transport interface {
	sendMsgAndWaitResponse(target *Endpoint, msg []byte) []byte

	sendMsg(target *Endpoint, msg []byte) error

	readMsg() (*Endpoint, []byte)
}
