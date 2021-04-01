package swimmer

import (
	"bytes"
	"encoding/json"
)

const (
	pingMessageType messageType = iota
	pingAckMessageType
	joinMessageType
	joinResponseMessageType
)

type message struct {
	messageType
	payload interface{}
}

type InboundMessage struct {
	From        Endpoint
	messageType messageType
	payload     interface{}
}

type JoinMessage struct {
	From           Endpoint
	KnownEndpoints []Endpoint
}

type JoinResponseMessage struct {
	KnownEndpoints []Endpoint
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


