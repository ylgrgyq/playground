package main

import (
	"fmt"
	"github.com/gorilla/websocket"
	"log"
	"net"
	"net/http"
	"time"
)

var upgrader = websocket.Upgrader{} // use default options

func echo(w http.ResponseWriter, r *http.Request) {
	c, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Print("upgrade:", err)
		return
	}

	h := func(message string) error {
		wsdogLogger.Infof("receive ping")
		err := c.WriteControl(websocket.PongMessage, []byte(message), time.Now().Add(time.Second))
		if err == websocket.ErrCloseSent {
			return nil
		} else if e, ok := err.(net.Error); ok && e.Temporary() {
			return nil
		}
		return err
	}

	c.SetPingHandler(h)
	defer c.Close()
	for {
		mt, message, err := c.ReadMessage()
		if err != nil {
			log.Println("read:", err)
			break
		}
		log.Printf("recv: %s, type: %d", message, mt)
		err = c.WriteMessage(mt, message)
		if err != nil {
			log.Println("write:", err)
			break
		}
	}
}

func runAsServer(cliOpts CommandLineArguments) {
	http.HandleFunc("/", echo)

	log.Fatal(http.ListenAndServe(fmt.Sprintf("localhost:%d",cliOpts.ListenPort), nil))
}
