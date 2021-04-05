package swimmer

import "testing"

func TestJoinMessage(t *testing.T) {
	type InboundMessage struct {
		From        Endpoint
		messageType MessageType
		payload     interface{}
	}

	type JoinMessage struct {
		From           Endpoint
		KnownEndpoints []Endpoint
	}

	joinMsg := JoinMessage{
		KnownEndpoints: []Endpoint{
			Endpoint{Name: "B", Address: []byte("001"), Status: AliveStateType},
			Endpoint{Name: "B", Address: []byte("001"), Status: AliveStateType},
			Endpoint{Name: "B", Address: []byte("001"), Status: AliveStateType},
		},
	}

	msg := InboundMessage{
		From:    Endpoint{Name: "A", Address: []byte("001"), Status: AliveStateType},
		payload: joinMsg,
	}

	encodePayload(joinMessageType, joinMsg)

}
