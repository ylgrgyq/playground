package consensus

import (
	"bytes"
	"context"
	"fmt"
	"google.golang.org/protobuf/proto"
	"io/ioutil"
	"net/http"
	"time"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type Endpoint struct {
	IP     string
	Port   uint16
}

type RpcClient interface {
	RequestVote(nodeEndpoint Endpoint, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	AppendEntries(nodeEndpoint Endpoint, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
}

type RpcHandler interface {
	HandleRequestVote(request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	HandleAppendEntries(request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
}

type HttpRpc struct {
	server *http.Server
}

type RequestApi string

const (
	RequestVoteApi   = "/requestVote"
	AppendEntriesApi = "/appendEntries"
)

func NewHttpRpc(boundIp string, port uint16, rpcHandler RpcHandler) *HttpRpc {
	serverMux := http.NewServeMux()
	serverMux.HandleFunc(RequestVoteApi, func(writer http.ResponseWriter, request *http.Request) {
		var req protos.RequestVoteRequest
		if !decodeRequest(writer, request, &req) {
			return
		}

		serverLogger.Debugf("receive request vote request: %+v", req)

		res, err := rpcHandler.HandleRequestVote(&req)
		if err != nil {
			serverLogger.Debugf("handle request vote failed. request: %+v, error: %s", req, err)
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}

		writeResponse(writer, request, res)
	})

	serverMux.HandleFunc(AppendEntriesApi, func(writer http.ResponseWriter, request *http.Request) {
		var req protos.AppendEntriesRequest
		if !decodeRequest(writer, request, &req) {
			return
		}

		serverLogger.Debugf("receive append entries request: %+v", req)

		res, err := rpcHandler.HandleAppendEntries(&req)
		if err != nil {
			serverLogger.Debugf("handle append entries failed. request: %+v, error: %s", req, err)
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

func (h *HttpRpc) Start() error {
	return h.server.ListenAndServe()
}

func (h *HttpRpc) Shutdown(context context.Context) error {
	return h.server.Shutdown(context)
}

func (h *HttpRpc) RequestVote(nodeEndpoint Endpoint, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	var res protos.RequestVoteResponse
	err := sendRequest(nodeEndpoint, RequestVoteApi, request, &res)
	if err != nil {
		return nil, err
	}

	return &res, nil
}

func (h *HttpRpc) AppendEntries(nodeEndpoint Endpoint, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	var res protos.AppendEntriesResponse
	err := sendRequest(nodeEndpoint, AppendEntriesApi, request, &res)
	if err != nil {
		return nil, err
	}

	return &res, nil
}

func decodeRequest(writer http.ResponseWriter, request *http.Request, requestDecoded proto.Message) bool {
	bs, err := ioutil.ReadAll(request.Body)
	if err != nil {
		serverLogger.Errorf("request url: %s. read body failed: %s", request.URL, err)
		http.Error(writer, err.Error(), http.StatusBadRequest)
	}

	err = proto.Unmarshal(bs, requestDecoded)
	if err != nil {
		serverLogger.Errorf("request url: %s. decode request failed: %s", request.URL, err)
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return false
	}
	return true
}

func writeResponse(writer http.ResponseWriter, request *http.Request, response proto.Message) {
	bs, err := proto.Marshal(response)
	if err != nil {
		serverLogger.Errorf("request url: %s. marshall response failed: %s", request.URL, err)
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}

	_, err = writer.Write(bs)
	if err != nil {
		serverLogger.Errorf("request url: %s. write response failed: %s", request.URL, err)
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
}

func sendRequest(endpoint Endpoint, api RequestApi, req proto.Message, resp proto.Message) error {
	bs, err := proto.Marshal(req)
	if err != nil {
		return err
	}

	url := buildRequestUrl(endpoint, api)
	postResp, err := http.Post(url, "application/octet-stream", bytes.NewBuffer(bs))
	if err != nil {
		return err
	}

	respBs, err := ioutil.ReadAll(postResp.Body)
	if err != nil {
		serverLogger.Errorf("read response body for request: %+v failed. error: %s", req, err)
		return err
	}

	err = proto.Unmarshal(respBs, resp)
	if err != nil {
		serverLogger.Errorf("decode response for request: %+v failed. error: %s", req, err)
		return err
	}

	return nil
}

func buildRequestUrl(endpoint Endpoint, api RequestApi) string {
	return fmt.Sprintf("http://%s:%d%s", endpoint.IP, endpoint.Port, api)
}
