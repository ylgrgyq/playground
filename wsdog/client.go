package main

import (
	"crypto/tls"
	"encoding/base64"
	"fmt"
	"github.com/gorilla/websocket"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const defaultHandshakeTimeout = 5 * time.Second
const defaultWriteWaitDuration = 5 * time.Second
const defaultCloseStatusCode = 1000
const defaultCloseReason = ""
const subProtocolHeader = "Sec-WebSocket-Protocol"

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

func newDialer(cliOpts ConnectOptions) websocket.Dialer {
	var tlsConfig = tls.Config{}
	if cliOpts.NoTlsCheck {
		tlsConfig.InsecureSkipVerify = true
	}
	return websocket.Dialer{
		TLSClientConfig:  &tlsConfig,
		Subprotocols:     []string{cliOpts.Subprotocol},
		Proxy:            http.ProxyFromEnvironment,
		HandshakeTimeout: defaultHandshakeTimeout,
	}
}

func buildConnectHeaders(cliOpts ConnectOptions) http.Header {
	headers := http.Header{}
	if len(cliOpts.Origin) > 0 {
		headers["Origin"] = []string{cliOpts.Origin}
	}

	if len(cliOpts.Host) > 0 {
		headers["Host"] = []string{cliOpts.Host}
	}

	if len(cliOpts.Headers) > 0 {
		for k, v := range cliOpts.Headers {
			headers[k] = []string{v}
		}
	}

	if len(cliOpts.Auth) > 0 {
		auth := fmt.Sprintf("Basic %s", base64.StdEncoding.EncodeToString([]byte(cliOpts.Auth)))
		headers["Authorization"] = []string{auth}
	}
	return headers
}

type Client struct {
	conn    *websocket.Conn
	done    chan struct{}
	cliOpts ConnectOptions
}

func (client *Client) doWriteMessage(messageType int, message []byte) {

	if err := client.conn.SetWriteDeadline(time.Now().Add(defaultWriteWaitDuration)); err != nil {
		panic(err)
	}

	err := client.conn.WriteMessage(messageType, message)
	if err != nil {
		panic(err)
	}
}

func (client *Client) writeMessage(input string) {
	if !client.cliOpts.EnableSlash || input[0:1] != "/" {
		client.doWriteMessage(websocket.TextMessage, []byte(input))
		return
	}

	switch cmd := input[1:]; {
	case cmd == "ping":
		client.doWriteMessage(websocket.PingMessage, nil)
	case cmd == "pong":
		client.doWriteMessage(websocket.PongMessage, nil)
	case strings.HasPrefix(cmd, "close"):
		statusCode := defaultCloseStatusCode
		reason := defaultCloseReason
		re := regexp.MustCompile("\\s+")
		toks := re.Split(input, -1)
		if len(toks) >= 2 {
			var err error
			if statusCode, err = strconv.Atoi(toks[1]); err != nil {
				wsdogLogger.Errorf("invalid close status code: \"%s\"", toks[1])
			}
		}

		if len(toks) >= 3 {
			reason = strings.Join(toks[2:], " ")
		}

		message := websocket.FormatCloseMessage(statusCode, reason)
		client.doWriteMessage(websocket.CloseMessage, message)
	default:
		client.doWriteMessage(websocket.TextMessage, []byte(input))
	}
}

func (client *Client) normalCloseConn() {
	// Cleanly close the connection by sending a close message and then
	// waiting (with timeout) for the server to close the connection.
	err := client.conn.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
	if err != nil {
		panic(err)
	}
	select {
	case <-client.done:
	case <-time.After(time.Second):
	}
}

func (client *Client) executeCommandThenShutdown(readWsChan chan WebSocketMessage) {
	client.writeMessage(client.cliOpts.ExecuteCommand)

	timout := time.Second * time.Duration(client.cliOpts.Wait)
	ticker := time.NewTicker(timout)

	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)

	for {
		select {
		case <-ticker.C:
			client.normalCloseConn()
			return
		case <-client.done:
			return
		case message := <-readWsChan:
			wsdogLogger.ReceiveMessagef("< %s", message.payload)
		case <-interrupt:
			client.normalCloseConn()
			return
		}
	}
}

func (client *Client) loopExecuteCommandFromConsole(readWsChan chan WebSocketMessage) {
	consoleReader := newConsoleInputReader()
	defer consoleReader.close()

	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)

	for {
		select {
		case <-client.done:
			return
		case message := <-readWsChan:
			wsdogLogger.ReceiveMessagef("< %s", message.payload)
		case output := <-consoleReader.outputChan:
			client.writeMessage(output)
		case <-interrupt:
			client.normalCloseConn()
			return
		}
	}
}

func (client *Client) run() {
	readWsChan := make(chan WebSocketMessage)

	setupReadForConn(client.conn, SetupReadOptions{client.done, readWsChan, client.cliOpts.ShowPingPong})

	if len(client.cliOpts.ExecuteCommand) > 0 {
		client.executeCommandThenShutdown(readWsChan)
	} else {
		client.loopExecuteCommandFromConsole(readWsChan)
	}
}

func (client *Client) mustClose() {
	if err := client.conn.Close(); err != nil {
		panic(err)
	}
}

func checkResponseSubprotocol(requiredProtocol string, resp *http.Response) {
	if len(resp.Header[subProtocolHeader]) < 1 || resp.Header[subProtocolHeader][0] != requiredProtocol {
		wsdogLogger.Fatal("error: Server sent no subprotocol")
	}
}

func runAsClient(url string, cliOpts ConnectOptions) {
	connectUrl := parseConnectUrl(url)

	dialer := newDialer(cliOpts)
	headers := buildConnectHeaders(cliOpts)

	conn, resp, err := dialer.Dial(connectUrl.String(), headers)
	if err != nil {
		wsdogLogger.Fatalf("connect to \"%s\" failed with error: \"%s\"", connectUrl, err)
	}

	if len(cliOpts.Subprotocol) > 0 {
		checkResponseSubprotocol(cliOpts.Subprotocol, resp)
	}

	wsdogLogger.Ok("Connected (press CTRL+C to quit)")

	client := Client{conn: conn, cliOpts: cliOpts, done: make(chan struct{})}

	defer client.mustClose()

	client.run()
}
