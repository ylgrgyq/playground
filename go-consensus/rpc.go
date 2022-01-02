package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	//"net/http"
	//"time"
	"net/http"
	"time"
)

type RequestVoteRequest struct {
	term         int64
	candidateId  string
	lastLogIndex int64
	lastLogTerm  int64
}

type RequestVoteResponse struct {
	term        int64
	voteGranted bool
}

type AppendEntriesRequest struct {
	term         int64
	leaderId     string
	prevLogIndex int64
	prevLogTerm  int64
	entries      []byte
	leaderCommit int64
}

type AppendEntriesResponse struct {
	term    int64
	success bool
}

type ErrorResponse struct {
	errorCode    int32
	errorMessage string
}

func (e *ErrorResponse) Error() string {
	return fmt.Sprintf("code: %d, message: %s", e.errorCode, e.errorMessage)
}

type NodeEndpoint struct {
	nodeId string
	ip     string
	port   uint16
}

type RpcClient interface {
	RequestVote(nodeEndpoint NodeEndpoint, request RequestVoteRequest) (*RequestVoteResponse, error)
	AppendEntries(nodeEndpoint NodeEndpoint, request AppendEntriesRequest) (*AppendEntriesResponse, error)
}

type RpcHandler interface {
	HandleRequestVote(request RequestVoteRequest) (RequestVoteResponse, error)
	HandleAppendEntries(request AppendEntriesRequest) (AppendEntriesResponse, error)
}

type HttpRpc struct {
	server *http.Server
}

type RequestApi string

const (
	RequestVoteApi = "/requestVote"
	AppendEntriesApi = "/appendEntries"
)

func newHttpRpc(boundIp string, port uint16, rpcHandler RpcHandler) *HttpRpc {
	serverMux := http.NewServeMux()
	serverMux.HandleFunc(RequestVoteApi, func(writer http.ResponseWriter, request *http.Request) {
		var req RequestVoteRequest
		if !decodeRequest(writer, request, req) {
			return
		}

		res, err := rpcHandler.HandleRequestVote(req)
		if err != nil {
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}

		writeResponse(writer, request, res)
	})

	serverMux.HandleFunc(AppendEntriesApi, func(writer http.ResponseWriter, request *http.Request) {
		var req AppendEntriesRequest
		if !decodeRequest(writer, request, req) {
			return
		}

		res, err := rpcHandler.HandleAppendEntries(req)
		if err != nil {
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}

		writeResponse(writer, request, res)
	})
	serverMux.Handle("/", http.NotFoundHandler())

	addr := fmt.Sprintf("%s:%d", boundIp, port)

	server := &http.Server{
		Addr:           addr,
		Handler:        serverMux,
		ReadTimeout:    10 * time.Second,
		WriteTimeout:   10 * time.Second,
		MaxHeaderBytes: 1 << 20,
	}

	return &HttpRpc{server: server}
}

func decodeRequest(writer http.ResponseWriter, request *http.Request, requestDecoded interface{}) bool {
	err := json.NewDecoder(request.Body).Decode(&requestDecoded)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return true
	}
	return false
}

func writeResponse(writer http.ResponseWriter, request *http.Request, response interface{}) {
	err := json.NewEncoder(writer).Encode(response)
	if err != nil {
		serverLogger.Errorf("request url: %s. encode response failed: %s", request.URL, err)
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
}

func sendRequest(endpoint NodeEndpoint, api RequestApi, req interface{}, resp interface{}) error {
	jsonBytes, err := json.Marshal(req)
	if err != nil {
		return err
	}

	url := buildRequestUrl(endpoint, api)
	postResp, err := http.Post(url, "application/json", bytes.NewBuffer(jsonBytes))
	if err != nil {
		return err
	}

	err = json.NewDecoder(postResp.Body).Decode(&resp)
	if err != nil {
		return err
	}

	return nil
}

func buildRequestUrl(endpoint NodeEndpoint, api RequestApi) string {
	return fmt.Sprintf("http://%s:%d/%s", endpoint.ip, endpoint.port, api)
}

func (h *HttpRpc) Start() error {
	return h.server.ListenAndServe()
}

func (h *HttpRpc) Shutdown(context context.Context) error {
	return h.server.Shutdown(context)
}

func (h *HttpRpc) RequestVote(nodeEndpoint NodeEndpoint, request RequestVoteRequest) (*RequestVoteResponse, error){
	var res RequestVoteResponse
	err := sendRequest(nodeEndpoint, RequestVoteApi, request, res)
	if err != nil {
		return nil, err
	}

	return &res, nil
}

func (h *HttpRpc) AppendEntries(nodeEndpoint NodeEndpoint, request AppendEntriesRequest) (*AppendEntriesResponse, error){
	var res AppendEntriesResponse
	err := sendRequest(nodeEndpoint, AppendEntriesApi, request, res)
	if err != nil {
		return nil, err
	}

	return &res, nil

}