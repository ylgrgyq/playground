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

type StateType int

const (
	LeaderState = iota + 1
	FollowerState
	CandidateState
)

type State interface {
	Start()
	StateType() StateType
	HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
	HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	HandleElectionTimeout()
	Stop()
}

type Leader struct {
	node *Node
}

func (_ *Leader) Start() {
}

func (_ *Leader) Stop() {
}

func (_ *Leader) StateType() StateType {
	return LeaderState
}

func (s *Leader) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	return nil, nil
}

func (s *Leader) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	return nil, nil
}

func (s *Leader) HandleElectionTimeout() {

}

type Follower struct {
	node                      *Node
	leaderId                  string
	cancelElectionTimeoutFunc context.CancelFunc
	startElectionTimeout      int64
}

func (f *Follower) Start() {
	f.cancelElectionTimeoutFunc = f.node.scheduleElectionTimeout(f.startElectionTimeout)
}

func (f *Follower) Stop() {
	f.cancelElectionTimeoutFunc()
}

func (f *Follower) StateType() StateType {
	return FollowerState
}

func (f *Follower) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	return nil, nil
}

func (f *Follower) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	return nil, nil
}

func (f *Follower) HandleElectionTimeout() {
	f.node.transferToCandidate()
}

type Candidate struct {
	node                      *Node
	cancelElectionTimeoutFunc context.CancelFunc
	startElectionTimeout      int64
}

func (c *Candidate) Start() {
	c.electAsLeader()
}

func (c *Candidate) Stop() {
	c.cancelElectionTimeoutFunc()
}

func (_ *Candidate) StateType() StateType {
	return CandidateState
}

func (c *Candidate) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	return nil, nil
}

func (c *Candidate) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	return nil, nil
}

func (c *Candidate) HandleElectionTimeout() {
	c.electAsLeader()
}

func (c *Candidate) electAsLeader() {
	n := c.node
	serverLogger.Debugf("%s start elect", n.id)
	meta := n.meta.GetMeta()
	meta.CurrentTerm += 1
	if err := n.meta.SaveMeta(meta); err != nil {
		serverLogger.Errorf("%s save meta failed. schedule election timeout latter. err=%s", n.id, err)
		c.cancelElectionTimeoutFunc = n.scheduleElectionTimeout(n.raftConfigs.ElectionTimeoutMs)
		return
	}

	reqs := n.buildRequestVoteRequests(meta)
	reps := n.broadcastRequestVote(reqs)

	var votes int
	for peerId, res := range reps {
		if Term(res.Term) > n.meta.GetMeta().CurrentTerm {
			peer := n.peers[peerId]
			n.transferToFollower(peer)
			return
		}

		if res.VoteGranted {
			votes += 1
		}
	}
	if votes > (len(n.peers)+1)/2 {
		n.transferToLeader()
		return
	}

	serverLogger.Okf("%s elect as leader failed, try elect leader later", n.id)
	c.cancelElectionTimeoutFunc = n.scheduleElectionTimeout(n.raftConfigs.ElectionTimeoutMs)
}

func EndpointId(e protos.Endpoint) string {
	return fmt.Sprintf("%s:%d", e.Ip, e.Port)
}

type PeerNode struct {
	Id       string
	Endpoint protos.Endpoint
}

func NewPeerNodes(es []protos.Endpoint) map[string]PeerNode {
	peers := make(map[string]PeerNode)
	for _, e := range es {
		id := EndpointId(e)
		peer := PeerNode{Id: id, Endpoint: e}
		peers[id] = peer
	}
	return peers
}

type Term int64
type Index int64

type Node struct {
	id                        string
	selfEndpoint              protos.Endpoint
	peers                     map[string]PeerNode
	state                     State
	meta                      MetaStorage
	logStorage                LogStorage
	commitIndex               Index
	lastApplied               Index
	scheduler                 Scheduler
	raftConfigs               RaftConfigurations
	rpcClient                 RpcClient
	cancelElectionTimeoutFunc context.CancelFunc
	Done                      chan struct{}
}

func newNode(configs *Configurations, rpcClient RpcClient) *Node {
	node := Node{
		id:                        EndpointId(configs.SelfEndpoint),
		selfEndpoint:              configs.SelfEndpoint,
		peers:                     NewPeerNodes(configs.PeerEndpoints),
		commitIndex:               0,
		lastApplied:               0,
		meta:                      NewTestingMeta(),
		scheduler:                 NewScheduler(),
		raftConfigs:               configs.RaftConfigurations,
		rpcClient:                 rpcClient,
		cancelElectionTimeoutFunc: nil,
		logStorage:                &TestingLogStorage{},
		Done:                      make(chan struct{}),
	}

	return &node
}

func (n *Node) Start() error {
	err := n.meta.Start()
	if err != nil {
		return fmt.Errorf("start meta failed. %s", err)
	}

	if len(n.peers) > 0 {
		initElectionTimeout := rand.Int63n(n.raftConfigs.ElectionTimeoutMs)
		n.transferState(&Follower{
			node:                 n,
			startElectionTimeout: initElectionTimeout,
			leaderId:             "",
		})
		return nil
	}

	n.transferToLeader()
	return nil
}

func (n *Node) scheduleElectionTimeout(electionTime int64) context.CancelFunc {
	ctx, cancelFunc := context.WithCancel(context.Background())
	n.scheduler.ScheduleOnce(ctx, time.Millisecond*time.Duration(electionTime), func() {
		if ctx.Err() != nil {
			serverLogger.Okf("election timeout canceled. %s", ctx.Err())
			return
		}
		n.state.HandleElectionTimeout()
	})
	return cancelFunc
}

func (n *Node) broadcastRequestVote(reqs map[string]*protos.RequestVoteRequest) map[string]*protos.RequestVoteResponse {
	responses := make(map[string]*protos.RequestVoteResponse)
	for peerId, req := range reqs {
		peer := n.peers[peerId]
		res, err := n.rpcClient.RequestVote(peer.Endpoint, req)
		if err != nil {
			serverLogger.Okf("request vote for peer: %s failed. %s", peerId, err)
			continue
		}
		responses[peerId] = res
	}

	return responses
}

func (n *Node) buildRequestVoteRequests(meta Meta) map[string]*protos.RequestVoteRequest {
	reqs := make(map[string]*protos.RequestVoteRequest)
	lastLog := n.logStorage.LastEntry()
	for peerId, peer := range n.peers {
		req := protos.RequestVoteRequest{
			Term:         int64(meta.CurrentTerm),
			CandidateId:  peer.Id,
			LastLogIndex: lastLog.Index,
			LastLogTerm:  lastLog.Term,
		}
		reqs[peerId] = &req
	}
	return reqs
}

func (n *Node) transferToLeader() {
	serverLogger.Okf("change self to leader")
	n.transferState(&Leader{node: n})
}

func (n *Node) transferToFollower(peer PeerNode) {
	serverLogger.Okf("choose %s as leader", peer.Id)
	n.transferState(&Follower{node: n})
}

func (n *Node) transferToCandidate() {
	n.transferState(&Candidate{node: n})
}

func (n *Node) transferState(newState State) {
	if oldState := n.state; oldState != nil {
		serverLogger.Okf("%s transfer state from %d to %d", n.id, n.state.StateType(), newState.StateType())
		oldState.Stop()
	} else {
		serverLogger.Okf("%s transfer to init state %d", n.id, newState.StateType())
	}

	n.state = newState
	n.state.Start()
}

func (n *Node) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	return n.state.HandleRequestVote(ctx, request)
}

func (n *Node) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	return n.state.HandleAppendEntries(ctx, request)
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

	rpcClient := rpcService.GetRpcClient()
	node := newNode(config, rpcClient)

	if err = rpcService.RegisterRpcHandler(node); err != nil {
		serverLogger.Fatalf("create rpc service failed: %s", err)
	}

	go func() {
		if err := rpcService.Start(); err != nil {
			serverLogger.Fatalf("start rpc service failed: %s", err)
		}
	}()

	if err = node.Start(); err != nil {
		serverLogger.Fatalf("start node failed: %s", err)
	}

	for {
		select {
		case <-node.Done:
			serverLogger.Ok("node %s done", node.id)
		}
	}

	//selfEndpoint := protos.Endpoint{
	//	Ip:   "127.0.0.1",
	//	Port: 8081,
	//}
	//
	//appendReq := protos.AppendEntriesRequest{
	//	Term:         121,
	//	LeaderId:     "101",
	//	PrevLogIndex: 1222,
	//	PrevLogTerm:  1223,
	//	Entries:      []byte{},
	//	LeaderCommit: 1232,
	//}
	//resp, err := rpcClient.AppendEntries(selfEndpoint, &appendReq)
	//if err != nil {
	//	serverLogger.Fatalf("append failed: %s", err)
	//}
	//
	//serverLogger.Okf("Append Response: %+v", resp)
	//
	//reqVote := protos.RequestVoteRequest{
	//	Term:         2121,
	//	CandidateId:  "2323",
	//	LastLogIndex: 3232,
	//	LastLogTerm:  133333,
	//}
	//resp2, err := rpcClient.RequestVote(selfEndpoint, &reqVote)
	//if err != nil {
	//	serverLogger.Fatalf("request vote failed: %s", err)
	//}
	//
	//serverLogger.Okf("Request vote Response: %+v", resp2)
	//
	//serverLogger.Ok("Ok")
}
