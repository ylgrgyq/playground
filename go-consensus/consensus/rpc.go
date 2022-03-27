package consensus

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"google.golang.org/protobuf/proto"
	"io/ioutil"
	"net/http"
	"net/url"
	"time"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type RpcAPI int

const (
	RequestVote RpcAPI = iota + 1
	AppendEntries
)

type RpcContextKeyType string

const RawRequestKey RpcContextKeyType = "RawRequest"

type RpcService interface {
	Start() error
	Shutdown(context context.Context) error
	RegisterRpcHandler(handler RpcHandler) error
	GetRpcClient() RpcClient
}

type RpcClient interface {
	RequestVote(nodeEndpoint protos.Endpoint, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	AppendEntries(nodeEndpoint protos.Endpoint, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
}

type RpcHandler interface {
	HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
}

func NewRpcService(rpcType RpcType, endpoint protos.Endpoint) (RpcService, error) {
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
	server       *http.Server
	apiToUriMap  map[RpcAPI]RequestApiUri
	rpcHandler   RpcHandler
	selfEndpoint protos.Endpoint
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
	if h.rpcHandler == nil {
		return errors.New("Http Rpc Service start before register any rpc handlers")
	}
 	return h.server.ListenAndServe()
}

func (h *HttpRpcService) Shutdown(context context.Context) error {
	return h.server.Shutdown(context)
}

func (h *HttpRpcService) RequestVote(nodeEndpoint protos.Endpoint, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	var res protos.RequestVoteResponse
	err := h.sendRequest(RequestVote, nodeEndpoint, request, &res)
	if err != nil {
		return nil, err
	}
	return &res, nil
}

func (h *HttpRpcService) AppendEntries(nodeEndpoint protos.Endpoint, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	var res protos.AppendEntriesResponse
	err := h.sendRequest(AppendEntries, nodeEndpoint, request, &res)
	if err != nil {
		return nil, err
	}

	return &res, nil
}

func (h *HttpRpcService) sendRequest(api RpcAPI, nodeEndpoint protos.Endpoint, requestBody proto.Message, responseBody proto.Message) error {
	uri, ok := h.apiToUriMap[api]
	if !ok {
		return fmt.Errorf("unknown rpc API with code: %d", api)
	}

	reqInBytes, err := h.encodeRequest(requestBody)
	if err != nil {
		serverLogger.Errorf("encode request body failed: %+v failed. error: %s", requestBody, err)
		return err
	}

	reqUrl := buildRequestUrl(nodeEndpoint, uri)
	postResp, err := http.Post(reqUrl, "application/octet-stream", bytes.NewBuffer(reqInBytes))
	if err != nil {
		return err
	}

	respBs, err := ioutil.ReadAll(postResp.Body)
	if err != nil {
		serverLogger.Errorf("read response body for request: %+v failed. error: %s", requestBody, err)
		return err
	}

	var rawResp protos.Response
	err = proto.Unmarshal(respBs, &rawResp)
	if err != nil {
		serverLogger.Errorf("decode response for request: %+v failed. error: %s", requestBody, err)
		return err
	}

	err = proto.Unmarshal(rawResp.Body, responseBody)
	if err != nil {
		serverLogger.Errorf("decode response body for request: %+v failed. error: %s", requestBody, err)
		return err
	}

	return nil
}

func (h *HttpRpcService) getRpcHandler() (RpcHandler, bool) {
	rpcHandler := h.rpcHandler
	if rpcHandler == nil {
		return nil, false
	}
	return rpcHandler, true
}

func NewHttpRpc(selfEndpoint protos.Endpoint) *HttpRpcService {
	httpRpcService := HttpRpcService{selfEndpoint: selfEndpoint}

	serverMux := http.NewServeMux()
	serverMux.HandleFunc(RequestVoteUri, httpRpcService.wrapRequestHandler(func(ctx context.Context, body []byte) (proto.Message, error) {
		var reqBody protos.RequestVoteRequest
		if err := decodeRequestBody(body, &reqBody); err != nil {
			return nil, err
		}

		serverLogger.Debugf("receive request vote request: %+v", reqBody)

		res, err := httpRpcService.rpcHandler.HandleRequestVote(ctx, &reqBody)
		if err != nil {
			return nil, err
		}

		return res, nil
	}))

	serverMux.HandleFunc(AppendEntriesUri, httpRpcService.wrapRequestHandler(func(ctx context.Context, body []byte) (proto.Message, error) {
		var reqBody protos.AppendEntriesRequest
		if err := decodeRequestBody(body, &reqBody); err != nil {
			return nil, err
		}

		serverLogger.Debugf("receive append entries request: %+v", reqBody)

		res, err := httpRpcService.rpcHandler.HandleAppendEntries(ctx, &reqBody)
		if err != nil {
			return nil, err
		}

		return res, nil
	}))

	serverMux.Handle("/", http.NotFoundHandler())

	httpRpcService.server = &http.Server{
		Addr:           fmt.Sprintf("%s:%d", selfEndpoint.Ip, selfEndpoint.Port),
		Handler:        serverMux,
		ReadTimeout:    10 * time.Second,
		WriteTimeout:   10 * time.Second,
		MaxHeaderBytes: 1 << 20,
	}
	httpRpcService.apiToUriMap = buildApiToUriMap()

	return &httpRpcService
}

type httpRpcError struct {
	message  string
	httpCode int
}

func (h *httpRpcError) Error() string {
	return h.message
}

func buildApiToUriMap() map[RpcAPI]RequestApiUri {
	var apiToUriMap = make(map[RpcAPI]RequestApiUri)
	apiToUriMap[RequestVote] = RequestVoteUri
	apiToUriMap[AppendEntries] = AppendEntriesUri
	return apiToUriMap
}

func (h *HttpRpcService) encodeRequest(requestBody proto.Message) ([]byte, error) {
	bs, err := proto.Marshal(requestBody)
	if err != nil {
		return nil, err
	}

	req := protos.Request{Body: bs, From: &h.selfEndpoint, Mid: RandString(8)}
	bs, err = proto.Marshal(&req)
	if err != nil {
		return nil, err
	}
	return bs, nil
}

func decodeRequestBody(body []byte, requestDecoded proto.Message) error {
	err := proto.Unmarshal(body, requestDecoded)
	if err != nil {
		return &httpRpcError{message: fmt.Sprintf("decode request failed: %s", err), httpCode: http.StatusBadRequest}
	}
	return nil
}

type requestHandler func(ctx context.Context, body []byte) (proto.Message, error)

func (h *HttpRpcService) wrapRequestHandler(f requestHandler) func(writer http.ResponseWriter, request *http.Request) {
	return func(writer http.ResponseWriter, request *http.Request) {
		if h.rpcHandler == nil {
			http.Error(writer, "http rpc service does not register any rpc handler", http.StatusServiceUnavailable)
			return
		}

		rawReq := decodeRequest(writer, request)
		if rawReq == nil {
			return
		}

		ctx := context.WithValue(request.Context(), RawRequestKey, rawReq)
		resp, err := f(ctx, rawReq.Body)
		if err != nil {
			serverLogger.Errorf("handle request failed. url: %s, error: %s", request.URL, err.Error())
			code := http.StatusInternalServerError
			e, ok := err.(*httpRpcError)
			if ok {
				code = e.httpCode
			}
			http.Error(writer, err.Error(), code)
			return
		}
		writeResponse(writer, request.URL, rawReq.Mid, resp)
	}
}

func decodeRequest(writer http.ResponseWriter, request *http.Request) *protos.Request {
	bs, err := ioutil.ReadAll(request.Body)
	if err != nil {
		serverLogger.Errorf("request url: %s. read body failed: %s", request.URL, err)
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return nil
	}

	var req protos.Request
	err = proto.Unmarshal(bs, &req)
	if err != nil {
		serverLogger.Errorf("request url: %s. decode request failed: %s", request.URL, err)
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return nil
	}
	return &req
}

func writeResponse(writer http.ResponseWriter, url *url.URL, mid string, responseBody proto.Message) {
	bs, err := proto.Marshal(responseBody)
	if err != nil {
		serverLogger.Errorf("request url: %s. marshall response body failed: %s", url, err)
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}

	resp := protos.Response{Body: bs, Mid: mid}
	bs, err = proto.Marshal(&resp)
	if err != nil {
		serverLogger.Errorf("request url: %s. marshall response failed: %s", url, err)
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}

	_, err = writer.Write(bs)
	if err != nil {
		serverLogger.Errorf("request url: %s. write response failed: %s", url, err)
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
}

func buildRequestUrl(endpoint protos.Endpoint, api RequestApiUri) string {
	return fmt.Sprintf("http://%s:%d%s", endpoint.Ip, endpoint.Port, api)
}
