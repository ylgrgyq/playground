package swimmer

import (
	"bytes"
	"encoding/json"
)

const (
	ping messageType = iota
	pingAck
	join
	joinResp
)

type message struct {
	messageType
	payload interface{}
}

type joinMessage struct {
	localEndpoint Endpoint
}

type joinMessageResp struct {
	remoteEndpoints []Endpoint
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


