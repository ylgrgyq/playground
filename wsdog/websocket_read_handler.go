package main

import (
	"github.com/gorilla/websocket"
	"net"
	"time"
)

type WebSocketMessage struct {
	messageType int
	payload     []byte
}

type SetupReadOptions struct {
	done         chan struct{}
	output       chan WebSocketMessage
	showPingPong bool
}

func SetupPingPongHandler(conn *websocket.Conn) {
	pingHandler := func(message string) error {
		wsdogLogger.ReceiveMessage("< Received ping")
		err := conn.WriteControl(websocket.PongMessage, []byte(message), time.Now().Add(defaultWriteWaitDuration))
		if err == websocket.ErrCloseSent {
			return nil
		} else if e, ok := err.(net.Error); ok && e.Temporary() {
			return nil
		}
		return err
	}

	pongHandler := func(message string) error {
		wsdogLogger.ReceiveMessage("< Received pong")
		return nil
	}

	conn.SetPingHandler(pingHandler)
	conn.SetPongHandler(pongHandler)

}

func setupReadForConn(conn *websocket.Conn, opts SetupReadOptions) {
	if opts.showPingPong {
		SetupPingPongHandler(conn)
	}

	go func() {
		defer close(opts.done)
		for {
			mt, message, err := conn.ReadMessage()
			if err != nil {
				closeErr, ok := err.(*websocket.CloseError)
				if ok {
					wsdogLogger.Okf("Disconnected (code: %d, reason: \"%s\")", closeErr.Code, closeErr.Text)
				} else {
					wsdogLogger.Errorf("error: %s", err.Error())
				}
				return
			}

			opts.output <- WebSocketMessage{mt, message}
		}
	}()
}
