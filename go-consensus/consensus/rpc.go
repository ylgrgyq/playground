package consensus

import (
	"bytes"
	"context"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"sync"
	"time"

	"google.golang.org/protobuf/proto"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type RpcAPI int

const (
	RequestVote RpcAPI = iota + 1
	AppendEntries
)

type RpcContextKeyType string

const RawRequestKey RpcContextKeyType = "RawRequest"

type RpcServiceHandlerUnregister func()

type RpcService interface {
	Start() error
	Shutdown(context context.Context) error
	RegisterRpcHandler(nodeId string, handler RpcHandler) (RpcServiceHandlerUnregister, error)
	GetRpcClient() RpcClient
}

type RpcResponse[T proto.Message] struct {
	response T
	err      error
}

type RpcClient interface {
	RequestVote(fromNodeId string, toNodeEndpoint *protos.Endpoint, request *protos.RequestVoteRequest) chan *RpcResponse[*protos.RequestVoteResponse]
	AppendEntries(fromNodeId string, toNodeEndpoint *protos.Endpoint, request *protos.AppendEntriesRequest) chan *RpcResponse[*protos.AppendEntriesResponse]
}

type RpcHandler interface {
	HandleRequestVote(ctx context.Context, from string, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	HandleAppendEntries(ctx context.Context, from string, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
}

func NewRpcService(logger *log.Logger, config *Configurations) (RpcService, error) {
	switch config.RpcType {
	case HttpRpcType:
		return NewHttpRpc(logger, config), nil
	default:
		return nil, fmt.Errorf("unsupport rpc type: %s", config.RpcType)
	}
}

type RequestApiUri string

const (
	RequestVoteUri   = "/requestVote"
	AppendEntriesUri = "/appendEntries"
)

type HttpRpcService struct {
	server         *http.Server
	client         *http.Client
	apiToUriMap    map[RpcAPI]RequestApiUri
	rpcHandlers    map[string]RpcHandler
	selfEndpoint   *protos.Endpoint
	logger         log.Logger
	rpcHandlerLock sync.Mutex
}

func (h *HttpRpcService) GetRpcClient() RpcClient {
	return h
}

func (h *HttpRpcService) RegisterRpcHandler(nodeId string, handler RpcHandler) (RpcServiceHandlerUnregister, error) {
	h.rpcHandlerLock.Lock()
	defer h.rpcHandlerLock.Unlock()

	if h.rpcHandlers == nil {
		h.rpcHandlers = make(map[string]RpcHandler)
	}

	if _, ok := h.rpcHandlers[nodeId]; ok {
		return nil, fmt.Errorf("node: %s has registered rpc handler", nodeId)
	}

	h.rpcHandlers[nodeId] = handler
	return func() {
		h.rpcHandlerLock.Lock()
		defer h.rpcHandlerLock.Unlock()
		delete(h.rpcHandlers, nodeId)
	}, nil
}

func (h *HttpRpcService) getRpcHandler(nodeId string) (RpcHandler, bool) {
	h.rpcHandlerLock.Lock()
	defer h.rpcHandlerLock.Unlock()
	handler, ok := h.rpcHandlers[nodeId]
	return handler, ok
}

func (h *HttpRpcService) Start() error {
	return h.server.ListenAndServe()
}

func (h *HttpRpcService) Shutdown(context context.Context) error {
	return h.server.Shutdown(context)
}

func (h *HttpRpcService) RequestVote(fromNodeId string, nodeEndpoint *protos.Endpoint, request *protos.RequestVoteRequest) chan *RpcResponse[*protos.RequestVoteResponse] {
	h.logger.Printf("send RequestVote from %s to %s. Term: %d, CandidateId: \"%s\", LastLogTerm: %d, LastLogIndex: %d",
		fromNodeId,
		nodeEndpoint.NodeId,
		request.Term,
		request.CandidateId,
		request.LastLogTerm,
		request.LastLogIndex)
	var res protos.RequestVoteResponse
	sendRetCh := h.sendRequest(RequestVote, fromNodeId, nodeEndpoint, request, &res)
	respCh := make(chan *RpcResponse[*protos.RequestVoteResponse])

	go func() {
		defer close(respCh)
		err := <-sendRetCh
		if err != nil {
			respCh <- &RpcResponse[*protos.RequestVoteResponse]{nil, err}
			return
		}
		h.logger.Printf("receive RequestVote response from %s to %s. Term: %d, VoteGranted: %t",
			nodeEndpoint.NodeId,
			fromNodeId,
			res.Term,
			res.VoteGranted)
		respCh <- &RpcResponse[*protos.RequestVoteResponse]{&res, nil}
	}()

	return respCh
}

func (h *HttpRpcService) AppendEntries(fromNodeId string, nodeEndpoint *protos.Endpoint, request *protos.AppendEntriesRequest) chan *RpcResponse[*protos.AppendEntriesResponse] {
	h.logger.Printf("send AppendEntries from %s to %s. Term: %d, LeaderId: \"%s\", LeaderCommit: %d",
		fromNodeId,
		nodeEndpoint.NodeId,
		request.Term,
		request.LeaderId,
		request.LeaderCommit)
	var res protos.AppendEntriesResponse
	sendRetCh := h.sendRequest(AppendEntries, fromNodeId, nodeEndpoint, request, &res)
	respCh := make(chan *RpcResponse[*protos.AppendEntriesResponse])
	go func() {
		defer close(respCh)
		err := <-sendRetCh
		if err != nil {
			respCh <- &RpcResponse[*protos.AppendEntriesResponse]{nil, err}
			return
		}
		h.logger.Printf("receive AppendEntries response from %s to %s. Term: %d, Success: %t",
			nodeEndpoint.NodeId,
			fromNodeId,
			res.Term,
			res.Success)
		respCh <- &RpcResponse[*protos.AppendEntriesResponse]{&res, nil}
	}()

	return respCh
}

func (h *HttpRpcService) sendRequest(
	api RpcAPI,
	fromNodeId string,
	toNodeEndpoint *protos.Endpoint,
	requestBody proto.Message,
	responseBody proto.Message,
) chan error {
	respChan := make(chan error)
	go func() {
		defer close(respChan)
		uri, ok := h.apiToUriMap[api]
		if !ok {
			h.logger.Printf("unknown rpc API with code: %d", api)
			respChan <- fmt.Errorf("unknown rpc API with code: %d", api)
			return
		}

		reqInBytes, err := h.encodeRequest(fromNodeId, toNodeEndpoint, requestBody)
		if err != nil {
			h.logger.Printf("encode request body failed: %+v failed. error: %s", requestBody, err)
			respChan <- err
			return
		}

		reqUrl := buildRequestUrl(toNodeEndpoint, uri)
		reqBody := bytes.NewBuffer(reqInBytes)
		postResp, err := h.client.Post(reqUrl, "application/octet-stream", reqBody)
		if err != nil {
			h.logger.Printf("post failed. error: %s", err)
			respChan <- err
			return
		}

		respBs, err := ioutil.ReadAll(postResp.Body)
		if err != nil {
			h.logger.Printf("read response body for request: %+v failed. error: %s", requestBody, err)
			respChan <- err
			return
		}

		var rawResp protos.Response
		err = proto.Unmarshal(respBs, &rawResp)
		if err != nil {
			h.logger.Printf("decode response for request: %+v failed. error: %s", requestBody, err)
			respChan <- err
			return
		}

		err = proto.Unmarshal(rawResp.Body, responseBody)
		if err != nil {
			h.logger.Printf("decode response body for request: %+v failed. error: %s", requestBody, err)
			respChan <- err
			return
		}

		respChan <- nil
	}()

	return respChan
}

func NewHttpRpc(logger *log.Logger, config *Configurations) *HttpRpcService {
	selfEndpoint := config.SelfEndpoint
	httpRpcServiceLogger := log.New(logger.Writer(), "[HttpRpc]", logger.Flags())
	httpClient := http.Client{
		Timeout: time.Duration(config.RpcTimeoutMs) * time.Millisecond,
	}
	httpRpcService := HttpRpcService{selfEndpoint: selfEndpoint, logger: *httpRpcServiceLogger, client: &httpClient, rpcHandlerLock: sync.Mutex{}}

	serverMux := http.NewServeMux()
	serverMux.HandleFunc(RequestVoteUri, httpRpcService.wrapRequestHandler(
		func(ctx context.Context, rawReq *protos.Request, handler RpcHandler, body []byte) (proto.Message, error) {
			var reqBody protos.RequestVoteRequest
			if err := decodeRequestBody(body, &reqBody); err != nil {
				return nil, err
			}

			httpRpcServiceLogger.Printf("receive RequestVote from %s, to %s. Term: %d, CandidateId: \"%s\", LastLogTerm: %d, LastLogIndex: %d",
				rawReq.From,
				rawReq.To,
				reqBody.Term,
				reqBody.CandidateId,
				reqBody.LastLogTerm,
				reqBody.LastLogIndex)

			res, err := handler.HandleRequestVote(ctx, rawReq.From, &reqBody)
			if err != nil {
				return nil, err
			}

			httpRpcServiceLogger.Printf("process RequestVote from: %s, to: %s done. Term: %d, VoteGranted: %t",
				rawReq.From,
				rawReq.To,
				res.Term,
				res.VoteGranted)

			return res, nil
		}))

	serverMux.HandleFunc(AppendEntriesUri, httpRpcService.wrapRequestHandler(
		func(ctx context.Context, rawReq *protos.Request, handler RpcHandler, body []byte) (proto.Message, error) {
			var reqBody protos.AppendEntriesRequest
			if err := decodeRequestBody(body, &reqBody); err != nil {
				return nil, err
			}

			httpRpcServiceLogger.Printf("receive AppendEntriesRequest from %s, to %s. Term: %d, LeaderId: \"%s\", LeaderCommit: %d",
				rawReq.From,
				rawReq.To,
				reqBody.Term,
				reqBody.LeaderId,
				reqBody.LeaderCommit)

			res, err := handler.HandleAppendEntries(ctx, rawReq.From, &reqBody)
			if err != nil {
				return nil, err
			}

			httpRpcServiceLogger.Printf("process AppendEntriesRequest from: %s, to: %s done. Term: %d, Success: %t",
				rawReq.From,
				rawReq.To,
				res.Term,
				res.Success)

			return res, nil
		}))

	serverMux.Handle("/*", http.NotFoundHandler())

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

func (h *HttpRpcService) encodeRequest(fromNodeId string, toNodeEndpoint *protos.Endpoint, requestBody proto.Message) ([]byte, error) {
	bs, err := proto.Marshal(requestBody)
	if err != nil {
		return nil, err
	}

	req := protos.Request{Body: bs, From: fromNodeId, To: toNodeEndpoint.NodeId, Mid: RandString(8)}
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

type requestHandler func(ctx context.Context, rawRequest *protos.Request, handler RpcHandler, body []byte) (proto.Message, error)

func (h *HttpRpcService) wrapRequestHandler(f requestHandler) func(writer http.ResponseWriter, request *http.Request) {
	return func(writer http.ResponseWriter, request *http.Request) {
		rawReq, err := decodeRequest(writer, request)
		if err != nil {
			h.logger.Printf("decode request failed. url: %s, error: %s", request.URL, err.Error())
			return
		}

		to := rawReq.To
		handler, ok := h.getRpcHandler(to)
		if !ok {
			http.Error(writer, fmt.Sprintf("no handler with nodeId: %s", to), http.StatusServiceUnavailable)
		}

		ctx := context.WithValue(request.Context(), RawRequestKey, rawReq)
		resp, err := f(ctx, rawReq, handler, rawReq.Body)
		if err != nil {
			h.logger.Printf("handle request failed. url: %s, error: %s", request.URL, err.Error())
			code := http.StatusInternalServerError
			e, ok := err.(*httpRpcError)
			if ok {
				code = e.httpCode
			}
			http.Error(writer, err.Error(), code)
			return
		}
		err = writeResponse(writer, request.URL, rawReq.Mid, resp)
		if err != nil {
			h.logger.Printf("write response failed. url: %s, error: %s", request.URL, err.Error())
		}
	}
}

func decodeRequest(writer http.ResponseWriter, request *http.Request) (*protos.Request, error) {
	bs, err := ioutil.ReadAll(request.Body)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return nil, fmt.Errorf("request url: %s. read body failed: %s", request.URL, err)
	}

	var req protos.Request
	err = proto.Unmarshal(bs, &req)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return nil, fmt.Errorf("request url: %s. decode request failed: %s", request.URL, err)
	}
	return &req, nil
}

func writeResponse(writer http.ResponseWriter, url *url.URL, mid string, responseBody proto.Message) error {
	bs, err := proto.Marshal(responseBody)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return fmt.Errorf("request url: %s. marshall response body failed: %s", url, err)
	}

	resp := protos.Response{Body: bs, Mid: mid}
	bs, err = proto.Marshal(&resp)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return fmt.Errorf("request url: %s. marshall response failed: %s", url, err)
	}

	_, err = writer.Write(bs)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return fmt.Errorf("request url: %s. write response failed: %s", url, err)
	}
	return nil
}

func buildRequestUrl(endpoint *protos.Endpoint, api RequestApiUri) string {
	return fmt.Sprintf("http://%s:%d%s", endpoint.Ip, endpoint.Port, api)
}
