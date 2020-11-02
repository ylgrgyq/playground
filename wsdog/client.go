package main

import (
	"github.com/gorilla/websocket"
	"log"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"time"
)

const defaultHandshakeTimeout = time.Second * time.Duration(5)

func parseConnectUrl(urlStr string) *url.URL {
	connectUrl, err := url.Parse(urlStr)
	if err != nil {
		wsdogLogger.Fatalf("\"%s\" is not a valid url", urlStr)
	}

	if connectUrl.Scheme == "" {
		wsdogLogger.Fatalf("missing scheme in url: \"%s\" to connect", urlStr)
	}

	if connectUrl.Host == "" {
		wsdogLogger.Fatalf("missing host in url: \"%s\" to connect", urlStr)
	}

	return connectUrl
}

func newDialer() websocket.Dialer {
	return websocket.Dialer{
		Proxy:            http.ProxyFromEnvironment,
		HandshakeTimeout: defaultHandshakeTimeout,
	}
}

func runAsClient(cliOpts CommandLineArguments) {
	connectUrl := parseConnectUrl(cliOpts.ConnectUrl)

	dialer := newDialer()
	c, _, err := dialer.Dial(connectUrl.String(), nil)
	if err != nil {
		wsdogLogger.Fatalf("connect to \"%s\" failed with error: \"%s\"", connectUrl, err)
	}

	wsdogLogger.Infof("Connected (press CTRL+C to quit)")

	defer c.Close()

	done := make(chan struct{})

	go func() {
		defer close(done)
		for {
			messageType, message, err := c.ReadMessage()
			if err != nil {
				closeErr, ok := err.(*websocket.CloseError)
				if ok {
					wsdogLogger.Infof("Disconnected (code: %d, reason: \"%s\")", closeErr.Code, closeErr.Text)
				} else {
					wsdogLogger.Infof("error: %s", err.Error())
				}
				return
			}

			switch messageType {
			case websocket.TextMessage:
				wsdogLogger.Infof("<: %s", message)
			case websocket.PongMessage:
				wsdogLogger.Infof("<: Received pong")
				//if cliOpts.ShowPingPong{
				//
				//}
			case websocket.PingMessage:
				wsdogLogger.Infof("<: Received ping")
				//if cliOpts.ShowPingPong {
				//
				//}
			}

		}
	}()

	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)

	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-done:
			return
		case _ = <-ticker.C:
			wsdogLogger.Infof("asdfasdf %b", cliOpts.ShowPingPong)
			c.SetWriteDeadline(time.Now().Add(time.Hour))
			err := c.WriteMessage(websocket.PingMessage, []byte(""))
			if err != nil {
				log.Println("error: ", err)
				return
			}
		case <-interrupt:
			// Cleanly close the connection by sending a close message and then
			// waiting (with timeout) for the server to close the connection.
			err := c.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
			if err != nil {
				log.Println("write close:", err)
				return
			}
			select {
			case <-done:
			case <-time.After(time.Second):
			}
			return
		}
	}
}
