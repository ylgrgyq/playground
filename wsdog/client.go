package main

import (
	"github.com/gorilla/websocket"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"time"
)

const defaultHandshakeTimeout = 5 * time.Second
const defaultWriteWaitDuration = 5 * time.Second

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

type Client struct {
	conn    *websocket.Conn
	done    chan struct{}
	cliOpts CommandLineArguments
}

func (client *Client) setupPingPongHandler() {
	if client.cliOpts.ShowPingPong {
		pingHandler := func(message string) error {
			wsdogLogger.Infof("< Received ping")
			err := client.conn.WriteControl(websocket.PongMessage, []byte(message), time.Now().Add(defaultWriteWaitDuration))
			if err == websocket.ErrCloseSent {
				return nil
			} else if e, ok := err.(net.Error); ok && e.Temporary() {
				return nil
			}
			return err
		}

		pongHandler := func(message string) error {
			wsdogLogger.Info("< Received pong")
			return nil
		}

		client.conn.SetPingHandler(pingHandler)
		client.conn.SetPongHandler(pongHandler)
	}
}

func (client *Client) setupReadFromConn() {
	client.setupPingPongHandler()
	go func() {
		defer close(client.done)
		for {
			_, message, err := client.conn.ReadMessage()
			if err != nil {
				closeErr, ok := err.(*websocket.CloseError)
				if ok {
					wsdogLogger.Infof("Disconnected (code: %d, reason: \"%s\")", closeErr.Code, closeErr.Text)
				} else {
					wsdogLogger.Infof("error: %s", err.Error())
				}
				return
			}

			wsdogLogger.Infof("< %s", message)
		}
	}()
}

func (client *Client) run() {
	client.setupReadFromConn()

	consoleReader := newConsoleInputReader()
	defer consoleReader.close()

	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)

	for {
		select {
		case <-client.done:
			return
		case text := <-consoleReader.outputChan:
			if err := client.conn.SetWriteDeadline(time.Now().Add(defaultWriteWaitDuration)); err != nil {
				panic(err)
			}

			err := client.conn.WriteMessage(websocket.TextMessage, []byte(text))
			if err != nil {
				panic(err)
			}
		case <-interrupt:
			// Cleanly close the connection by sending a close message and then
			// waiting (with timeout) for the server to close the connection.
			err := client.conn.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
			if err != nil {
				return
			}
			select {
			case <-client.done:
			case <-time.After(time.Second):
			}
			return
		}
	}
}

func (client *Client) close() {
	if err := client.conn.Close(); err != nil {
		panic(err)
	}
}

func runAsClient(cliOpts CommandLineArguments) {
	connectUrl := parseConnectUrl(cliOpts.ConnectUrl)

	dialer := newDialer()
	conn, _, err := dialer.Dial(connectUrl.String(), nil)
	if err != nil {
		wsdogLogger.Fatalf("connect to \"%s\" failed with error: \"%s\"", connectUrl, err)
	}

	wsdogLogger.Infof("Connected (press CTRL+C to quit)")

	client := Client{conn: conn, cliOpts: cliOpts, done: make(chan struct{})}

	defer client.close()

	client.run()
}
