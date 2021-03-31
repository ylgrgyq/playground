package swimmer

import (
	"bytes"
	"encoding/json"
)

const (
	pingMessageType messageType = iota
	pingAckMessageType
	joinMessageType
	joinRespMessageType
)

type message struct {
	messageType
	payload interface{}
}

type Message struct {
	from *Endpoint
	messageType messageType
	payload []byte
}

type joinMessage struct {
	from *Endpoint
	knownGroup []*Endpoint
}

type joinMessageResp struct {
	remoteEndpoints []*Endpoint
}

func encodeMessage(msgType messageType, payload interface{})(*bytes.Buffer, error) {
	payloadInBytes, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	buf := bytes.NewBuffer(nil)
	buf.WriteByte(uint8(msgType))
	buf.Write(payloadInBytes)

	return buf, nil
}

func decodePayload(payloadInBytes []byte, payload interface{}) error {
	return json.Unmarshal(payloadInBytes, payload)
}


