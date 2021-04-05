package swimmer

import (
	"bytes"
	"encoding/json"
	"fmt"
)

const (
	pingMessageType MessageType = iota
	pingAckMessageType
	joinMessageType
	joinResponseMessageType
	errorMessageType
)

type InboundMessage struct {
	From        Endpoint
	MessageType MessageType
	Payload     interface{}
}

type JoinMessage struct {
	KnownEndpoints []Endpoint
}

type JoinResponseMessage struct {
	KnownEndpoints []Endpoint
}

func encodePayload(msgType MessageType, payload interface{}) (*bytes.Buffer, error) {
	payloadInBytes, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	buf := bytes.NewBuffer(nil)
	buf.WriteByte(uint8(msgType))
	buf.Write(payloadInBytes)

	return buf, nil
}

func decodePayload(msgType MessageType, payloadInBytes []byte) (interface{}, error) {

	switch msgType {
	case joinMessageType:
		var joinMsg interface{}
		err := json.Unmarshal(payloadInBytes, joinMsg)
		if err != nil {
			return nil, err
		}
		return joinMsg, nil
	}
	return nil, fmt.Errorf("known supported message type")
}
