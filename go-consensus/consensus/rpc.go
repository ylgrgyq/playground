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
	IP   string
	Port uint16
}

func (e *Endpoint) String() string {
	return fmt.Sprintf("%s:%d", e.IP, e.Port)
}

type RpcAPI int

const (
	RequestVote RpcAPI = iota + 1
	AppendEntries
)

type RpcService interface {
	Start() error
	Shutdown(context context.Context) error
	RegisterRpcHandler(handler RpcHandler) error
	GetRpcClient() RpcClient
}

type RpcClient interface {
	RequestVote(nodeEndpoint Endpoint, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	AppendEntries(nodeEndpoint Endpoint, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
}

type RpcHandler interface {
	HandleRequestVote(request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	HandleAppendEntries(request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
}

func NewRpcService(rpcType RpcType, endpoint Endpoint) (RpcService, error) {
	switch rpcType {
	case HttpRpcType:
		return NewHttpRpc(endpoint), nil
	default:
		return nil, fmt.Errorf("unsupport rpc type: %s", rpcType)
	}
}

type RequestApiUri string

const (
	RequestVoteUri   = "/requestVote"
	AppendEntriesUri = "/appendEntries"
)

type HttpRpcService struct {
	server      *http.Server
	apiToUriMap map[RpcAPI]RequestApiUri
	rpcHandler  RpcHandler
}

func (h *HttpRpcService) GetRpcClient() RpcClient {
	return h
}

func (h *HttpRpcService) RegisterRpcHandler(handler RpcHandler) error {
	if h.rpcHandler != nil {
		return fmt.Errorf("http rpc service already has a rpc handler")
	}
	h.rpcHandler = handler
	return nil
}

func (h *HttpRpcService) Start() error {
	return h.server.ListenAndServe()
}

func (h *HttpRpcService) Shutdown(context context.Context) error {
	return h.server.Shutdown(context)
}

func (h *HttpRpcService) RequestVote(nodeEndpoint Endpoint, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	var res protos.RequestVoteResponse
	err := h.SendRequest(RequestVote, nodeEndpoint, request, &res)
	if err != nil {
		return nil, err
	}
	return &res, nil
}

func (h *HttpRpcService) AppendEntries(nodeEndpoint Endpoint, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	var res protos.AppendEntriesResponse
	err := h.SendRequest(AppendEntries, nodeEndpoint, request, &res)
	if err != nil {
		return nil, err
	}

	return &res, nil
}

func (h *HttpRpcService) SendRequest(api RpcAPI, nodeEndpoint Endpoint, request proto.Message, resp proto.Message) error {
	uri, ok := h.apiToUriMap[api]
	if !ok {
		return fmt.Errorf("unknown rpc API with code: %d", api)
	}

	bs, err := proto.Marshal(request)
	if err != nil {
		return err
	}

	url := buildRequestUrl(nodeEndpoint, uri)
	postResp, err := http.Post(url, "application/octet-stream", bytes.NewBuffer(bs))
	if err != nil {
		return err
	}

	respBs, err := ioutil.ReadAll(postResp.Body)
	if err != nil {
		serverLogger.Errorf("read response body for request: %+v failed. error: %s", request, err)
		return err
	}

	err = proto.Unmarshal(respBs, resp)
	if err != nil {
		serverLogger.Errorf("decode response for request: %+v failed. error: %s", request, err)
		return err
	}

	return nil
}

func (h *HttpRpcService) assertGetRpcHandler(writer http.ResponseWriter) RpcHandler {
	rpcHandler := h.rpcHandler
	if rpcHandler == nil {
		serverLogger.Debugf("http rpc service does not register any rpc handler")
		http.Error(writer, fmt.Sprintf("service not initialized"), http.StatusServiceUnavailable)
		return nil
	}
	return rpcHandler
}

func NewHttpRpc(selfEndpoint Endpoint) *HttpRpcService {
	var httpRpcService HttpRpcService

	serverMux := http.NewServeMux()
	serverMux.HandleFunc(RequestVoteUri, func(writer http.ResponseWriter, request *http.Request) {
		var req protos.RequestVoteRequest
		if !decodeRequest(writer, request, &req) {
			return
		}

		serverLogger.Debugf("receive request vote request: %+v", req)

		rpcHandler := httpRpcService.assertGetRpcHandler(writer)
		if rpcHandler == nil {
			return
		}
		res, err := rpcHandler.HandleRequestVote(&req)
		if err != nil {
			serverLogger.Debugf("handle request vote failed. request: %+v, error: %s", req, err)
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}

		writeResponse(writer, request, res)
	})

	serverMux.HandleFunc(AppendEntriesUri, func(writer http.ResponseWriter, request *http.Request) {
		var req protos.AppendEntriesRequest
		if !decodeRequest(writer, request, &req) {
			return
		}

		serverLogger.Debugf("receive append entries request: %+v", req)

		rpcHandler := httpRpcService.assertGetRpcHandler(writer)
		if rpcHandler == nil {
			return
		}
		res, err := rpcHandler.HandleAppendEntries(&req)
		if err != nil {
			serverLogger.Debugf("handle append entries failed. request: %+v, error: %s", req, err)
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}

		writeResponse(writer, request, res)
	})

	serverMux.Handle("/", http.NotFoundHandler())

	httpRpcService.server = &http.Server{
		Addr:           selfEndpoint.String(),
		Handler:        serverMux,
		ReadTimeout:    10 * time.Second,
		WriteTimeout:   10 * time.Second,
		MaxHeaderBytes: 1 << 20,
	}
	httpRpcService.apiToUriMap = buildApiToUriMap()

	return &httpRpcService
}

func buildApiToUriMap() map[RpcAPI]RequestApiUri {
	var apiToUriMap = make(map[RpcAPI]RequestApiUri)
	apiToUriMap[RequestVote] = RequestVoteUri
	apiToUriMap[AppendEntries] = AppendEntriesUri
	return apiToUriMap
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

func buildRequestUrl(endpoint Endpoint, api RequestApiUri) string {
	return fmt.Sprintf("http://%s:%d%s", endpoint.IP, endpoint.Port, api)
}
