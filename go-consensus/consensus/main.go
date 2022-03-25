package consensus

import (
	"context"
	"fmt"
	"github.com/jessevdk/go-flags"
	"math/rand"
	"os"
	"time"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type State interface {
	HandleAppendEntries()
	HandleRequestVote()
}

type Leader struct {
}

type Follower struct {
}

func (f *Follower) HandleAppendEntries() {

}

func (f *Follower) HandleRequestVote() {

}

type Candidate struct {
}

func (s *Leader) HandleAppendEntries() {

}

func EndpointId(e protos.Endpoint) string {
	return fmt.Sprintf("%s:%d", e.Ip, e.Port)
}

type PeerNode struct {
	Id       string
	Endpoint protos.Endpoint
}

func NewPeerNodes(es []protos.Endpoint) []PeerNode {
	var peers []PeerNode
	for _, e := range es {
		peers = append(peers, PeerNode{Id: EndpointId(e), Endpoint: e})
	}
	return peers
}

type Term int64
type Index int64

type Node struct {
	id           string
	selfEndpoint protos.Endpoint
	peers        []PeerNode
	state        State
	meta         MetaStorage
	logStorage   LogStorage
	commitIndex  Index
	lastApplied  Index
	scheduler    Scheduler
	raftConfigs  RaftConfigurations
	rpcClient    RpcClient
}

func newNode(configs Configurations, rpcClient RpcClient) *Node {
	return &Node{
		id:           EndpointId(configs.SelfEndpoint),
		selfEndpoint: configs.SelfEndpoint,
		peers:        NewPeerNodes(configs.PeerEndpoints),
		state:        &Follower{},
		commitIndex:  0,
		lastApplied:  0,
		meta:         NewTestingMeta(),
		scheduler:    NewScheduler(),
		raftConfigs:  configs.RaftConfigurations,
		rpcClient:    rpcClient,
	}
}

func (n *Node) Start() error {
	err := n.meta.Start()
	if err != nil {
		return fmt.Errorf("start meta failed. %s", err)
	}

	ctx := context.Background()

	initElectionTimeout := rand.Int63n(n.raftConfigs.ElectionTimeoutMs)
	n.scheduler.ScheduleOnce(ctx, time.Millisecond*time.Duration(initElectionTimeout), func() {
		reqs := n.requestVoteRequests()
		reps := n.broadcastRequestVote(reqs)

		
		for peer, res := range reps {
			if res.VoteGranted {

			}
		}
	})
	return nil
}

func (n *Node) broadcastRequestVote(reqs map[PeerNode]*protos.RequestVoteRequest) map[PeerNode]*protos.RequestVoteResponse{
	responses := make(map[PeerNode]*protos.RequestVoteResponse)
	for peer, req := range reqs {
		res, err := n.rpcClient.RequestVote(peer.Endpoint, req)
		if err != nil {
			serverLogger.Okf("request vote for peer: %s failed. %s", peer.Id, err)
			continue
		}
		responses[peer] = res
	}

	return responses
}

func (n *Node) requestVoteRequests() map[PeerNode]*protos.RequestVoteRequest {
	meta, err := n.meta.GetMeta()
	if err != nil {
		serverLogger.Fatalf("get meta failed", err)
	}

	reqs := make(map[PeerNode]*protos.RequestVoteRequest)
	lastLog := n.logStorage.LastEntry()
	for _, peer := range n.peers {
		req := protos.RequestVoteRequest{
			Term:         int64(meta.CurrentTerm),
			CandidateId:  peer.Id,
			LastLogIndex: lastLog.Index,
			LastLogTerm:  lastLog.Term,
		}
		reqs[peer] = &req
	}
	return reqs
}


type DummyRpcHandler struct {
}

func (d *DummyRpcHandler) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	resp := protos.RequestVoteResponse{
		Term:        22222,
		VoteGranted: true,
	}
	serverLogger.Debugf("receive req vote from %s", ctx.Value(RawRequestKey))
	return &resp, nil
}

func (d *DummyRpcHandler) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	resp := protos.AppendEntriesResponse{
		Term:    3321,
		Success: true,
	}
	return &resp, nil
}

type ApplicationOptions struct {
	ConfigurationFilePath string `short:"c" long:"config" description:"path to configuration file"`
}

type CommandLineOptions struct {
	ApplicationOptions
}

func parseCommandLineArguments() CommandLineOptions {
	var appOpts ApplicationOptions
	parser := flags.NewParser(&appOpts, flags.Default)

	if _, err := parser.Parse(); err != nil {
		switch flagsErr := err.(type) {
		case *flags.Error:
			if flagsErr.Type == flags.ErrHelp {
				os.Exit(0)
			}

			parser.WriteHelp(os.Stderr)
			os.Exit(1)
		default:
			parser.WriteHelp(os.Stderr)
			os.Exit(1)
		}
	}

	if appOpts.ConfigurationFilePath == "" {
		parser.WriteHelp(os.Stderr)
		os.Exit(1)
	}

	return CommandLineOptions{appOpts}
}

func Main() {
	cliOpts := parseCommandLineArguments()
	defaultLogger.EnableDebug()

	config, err := ParseConfig(cliOpts.ConfigurationFilePath)
	if err != nil {
		serverLogger.Fatalf("parse config failed: %s", err)
	}

	serverLogger.Okf("%s", config)

	rpcService, err := NewRpcService(config.RpcType, config.SelfEndpoint)
	if err != nil {
		serverLogger.Fatalf("create rpc service failed: %s", err)
	}

	rpcHandler := DummyRpcHandler{}
	if err = rpcService.RegisterRpcHandler(&rpcHandler); err != nil {
		serverLogger.Fatalf("create rpc service failed: %s", err)
	}

	rpcClient := rpcService.GetRpcClient()

	go func() {
		err := rpcService.Start()
		if err != nil {
			serverLogger.Fatalf("start rpc service failed: %s", err)
		}
	}()

	selfEndpoint := protos.Endpoint{
		Ip:   "127.0.0.1",
		Port: 8081,
	}

	appendReq := protos.AppendEntriesRequest{
		Term:         121,
		LeaderId:     "101",
		PrevLogIndex: 1222,
		PrevLogTerm:  1223,
		Entries:      []byte{},
		LeaderCommit: 1232,
	}
	resp, err := rpcClient.AppendEntries(selfEndpoint, &appendReq)
	if err != nil {
		serverLogger.Fatalf("append failed: %s", err)
	}

	serverLogger.Okf("Append Response: %+v", resp)

	reqVote := protos.RequestVoteRequest{
		Term:         2121,
		CandidateId:  "2323",
		LastLogIndex: 3232,
		LastLogTerm:  133333,
	}
	resp2, err := rpcClient.RequestVote(selfEndpoint, &reqVote)
	if err != nil {
		serverLogger.Fatalf("request vote failed: %s", err)
	}

	serverLogger.Okf("Request vote Response: %+v", resp2)

	serverLogger.Ok("Ok")
}
