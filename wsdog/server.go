package main

import (
	"fmt"
	"github.com/gorilla/websocket"
	"log"
	"net/http"
)

var upgrader = websocket.Upgrader{} // use default options

func closeConn(conn *websocket.Conn) {
	// failed to send the last close message is tolerable due to the connection may broken
	_ = conn.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))

	if err := conn.Close(); err != nil {
		panic(err)
	}
}

func generateWsHandler(cliOpts ListenOnPortOptions) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Print("upgrade:", err)
			return
		}

		readWsChan := make(chan WebSocketMessage)
		done := make(chan struct{})
		setupReadForConn(conn, SetupReadOptions{done, readWsChan, cliOpts.ShowPingPong})

		defer closeConn(conn)
		for {
			select {
			case <-done:
				return
			case message := <-readWsChan:
				wsdogLogger.ReceiveMessagef("< %s", message.payload)
				err = conn.WriteMessage(message.messageType, message.payload)
				if err != nil {
					panic(err)
				}
			}
		}
	}
}

func runAsServer(listenPort uint16, cliOpts ListenOnPortOptions) {
	http.HandleFunc("/", generateWsHandler(cliOpts))

	wsdogLogger.Okf("listening on port %d (press CTRL+C to quit)", listenPort)
	log.Fatal(http.ListenAndServe(fmt.Sprintf("localhost:%d", listenPort), nil))
}
