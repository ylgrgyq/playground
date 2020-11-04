package main

import (
	"fmt"
	"github.com/gorilla/websocket"
	"log"
	"net/http"
)

var upgrader = websocket.Upgrader{} // use default options

func closeConn(conn *websocket.Conn) {
	if err := conn.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, "")); err != nil {
		panic(err)
	}

	if err := conn.Close(); err != nil {
		panic(err)
	}
}

func generateWsHandler(cliOpts CommandLineArguments) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Print("upgrade:", err)
			return
		}

		readWsChan := make(chan []byte)
		done := make(chan struct{})
		setupReadForConn(conn, SetupReadOptions{done, readWsChan, cliOpts.ShowPingPong})

		defer closeConn(conn)
		for {
			select {
			case <-done:
				return
			case message := <-readWsChan:
				wsdogLogger.Infof("< %s", message)
				err = conn.WriteMessage(websocket.TextMessage, message)
				if err != nil {
					log.Println("write:", err)
				}
			}
		}
	}
}

func runAsServer(cliOpts CommandLineArguments) {
	http.HandleFunc("/", generateWsHandler(cliOpts))

	log.Fatal(http.ListenAndServe(fmt.Sprintf("localhost:%d", cliOpts.ListenPort), nil))
}
