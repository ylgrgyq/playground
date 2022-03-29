package consensus

import (
	"context"
	"fmt"
	"github.com/jessevdk/go-flags"
	"math/rand"
	"os"
	"sync"
	"time"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type StateType string

const (
	LeaderState = "Leader"
	FollowerState = "Follower"
	CandidateState = "Candidate"
)

type State interface {
	Start()
	StateType() StateType
	HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
	HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error)
	Stop()
}

type Leader struct {
	node                  *Node
	cancelPingTimeoutFunc context.CancelFunc
	scheduler             Scheduler
}

func (l *Leader) Start() {
	l.cancelPingTimeoutFunc = l.node.schedulePeriod(
		0,
		l.node.raftConfigs.PingTimeoutMs,
		func() {
			l.broadcastAppendEntries()
		})

}

func (l *Leader) Stop() {
	if l.cancelPingTimeoutFunc != nil {
		l.cancelPingTimeoutFunc()
		l.cancelPingTimeoutFunc = nil
	}
}

func (_ *Leader) StateType() StateType {
	return LeaderState
}

func (l *Leader) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	l.node.lock.Lock()
	defer l.node.lock.Unlock()

	meta := l.node.meta.GetMeta()
	reqTerm := request.Term
	if reqTerm < meta.CurrentTerm {
		return &protos.RequestVoteResponse{
			Term:        int64(meta.CurrentTerm),
			VoteGranted: false,
		}, nil
	}

	if reqTerm == meta.CurrentTerm {
		return &protos.RequestVoteResponse{
			Term:        int64(meta.CurrentTerm),
			VoteGranted: false,
		}, nil
	}

	lastEntry := l.node.logStorage.LastEntry()
	if !lastEntry.IsAtLeastUpToDateThanMe(request.LastLogTerm, request.LastLogIndex) {
		return &protos.RequestVoteResponse{
			Term:        int64(meta.CurrentTerm),
			VoteGranted: false,
		}, nil
	}

	peer := l.node.peers[request.CandidateId]
	meta = Meta{CurrentTerm: reqTerm, VoteFor: request.CandidateId}
	if err := l.node.meta.SaveMeta(meta); err != nil {
		return nil, err
	}
	l.node.transferToFollower(peer, l.node.raftConfigs.ElectionTimeoutMs)
	return &protos.RequestVoteResponse{
		Term:        int64(meta.CurrentTerm),
		VoteGranted: true,
	}, nil
}

func (l *Leader) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	l.node.lock.Lock()
	defer l.node.lock.Unlock()

	meta := l.node.meta.GetMeta()
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
}

func (l *Leader) broadcastAppendEntries() {
	heartbeat := l.buildHeartbeat()

	for peerId, peer := range l.node.peers {
		_, err := l.node.rpcClient.AppendEntries(peer.Endpoint, heartbeat)
		if err != nil {
			serverLogger.Okf("append entries to peer: %s failed. %s", peerId, err)
			continue
		}
		// todo handle heartbeat response
	}

}

func (l *Leader) buildHeartbeat() *protos.AppendEntriesRequest{
	l.node.lock.Lock()
	defer l.node.lock.Unlock()

	meta := l.node.meta.GetMeta()

	return &protos.AppendEntriesRequest{
		Term:         int64(meta.CurrentTerm),
		LeaderId:     l.node.id,
		PrevLogIndex: 100,
		PrevLogTerm:  100,
		Entries:      []byte{},
		LeaderCommit: 100,
	}
}

type Follower struct {
	node                      *Node
	leaderId                  string
	cancelElectionTimeoutFunc context.CancelFunc
	startElectionTimeout      int64
}

func (f *Follower) Start() {
	f.cancelElectionTimeoutFunc = f.node.scheduleOnce(f.startElectionTimeout, func() {
		f.node.lock.Lock()
		defer f.node.lock.Unlock()
		f.node.transferToCandidate()
	})
}

func (f *Follower) Stop() {
	if f.cancelElectionTimeoutFunc != nil {
		f.cancelElectionTimeoutFunc()
		f.cancelElectionTimeoutFunc = nil
	}
}

func (f *Follower) StateType() StateType {
	return FollowerState
}

func (f *Follower) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	f.node.lock.Lock()
	defer f.node.lock.Unlock()

	meta := f.node.meta.GetMeta()
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
}

func (f *Follower) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	f.node.lock.Lock()
	defer f.node.lock.Unlock()

	meta := f.node.meta.GetMeta()
	reqTerm := request.Term
	if reqTerm < meta.CurrentTerm {
		return &protos.RequestVoteResponse{
			Term:        meta.CurrentTerm,
			VoteGranted: false,
		}, nil
	}

	lastEntry := f.node.logStorage.LastEntry()
	if !lastEntry.IsAtLeastUpToDateThanMe(request.LastLogTerm, request.LastLogIndex) {
		return &protos.RequestVoteResponse{
			Term:        meta.CurrentTerm,
			VoteGranted: false,
		}, nil
	}

	if reqTerm == meta.CurrentTerm {
		if len(meta.VoteFor) > 0 && meta.VoteFor != request.CandidateId {
			return &protos.RequestVoteResponse{
				Term:        meta.CurrentTerm,
				VoteGranted: false,
			}, nil
		}
	}

	peer := f.node.peers[request.CandidateId]
	meta = Meta{CurrentTerm: reqTerm, VoteFor: request.CandidateId}
	if err := f.node.meta.SaveMeta(meta); err != nil {
		return nil, err
	}
	f.node.transferToFollower(peer, f.node.raftConfigs.ElectionTimeoutMs)
	return &protos.RequestVoteResponse{
		Term:        meta.CurrentTerm,
		VoteGranted: true,
	}, nil
}

type Candidate struct {
	node                      *Node
	cancelElectionTimeoutFunc context.CancelFunc
	startElectionTimeout      int64
}

func (c *Candidate) Start() {
	go func() {
		c.electAsLeader()
	}()
}

func (c *Candidate) Stop() {
	if c.cancelElectionTimeoutFunc != nil {
		c.cancelElectionTimeoutFunc()
		c.cancelElectionTimeoutFunc = nil
	}
}

func (_ *Candidate) StateType() StateType {
	return CandidateState
}

func (c *Candidate) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	c.node.lock.Lock()
	defer c.node.lock.Unlock()

	meta := c.node.meta.GetMeta()
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
}

func (c *Candidate) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	c.node.lock.Lock()
	defer c.node.lock.Unlock()

	meta := c.node.meta.GetMeta()
	reqTerm := request.Term
	if reqTerm < meta.CurrentTerm {
		return &protos.RequestVoteResponse{
			Term:        meta.CurrentTerm,
			VoteGranted: false,
		}, nil
	}

	if reqTerm == meta.CurrentTerm {
		return &protos.RequestVoteResponse{
			Term:        meta.CurrentTerm,
			VoteGranted: false,
		}, nil
	}

	lastEntry := c.node.logStorage.LastEntry()
	if !lastEntry.IsAtLeastUpToDateThanMe(request.LastLogTerm, request.LastLogIndex) {
		return &protos.RequestVoteResponse{
			Term:        meta.CurrentTerm,
			VoteGranted: false,
		}, nil
	}

	peer := c.node.peers[request.CandidateId]
	meta = Meta{CurrentTerm: reqTerm, VoteFor: request.CandidateId}
	if err := c.node.meta.SaveMeta(meta); err != nil {
		return nil, err
	}
	c.node.transferToFollower(peer, c.node.raftConfigs.ElectionTimeoutMs)
	return &protos.RequestVoteResponse{
		Term:        int64(meta.CurrentTerm),
		VoteGranted: true,
	}, nil
}

func (c *Candidate) electAsLeader() {
	n := c.node
	serverLogger.Debugf("%s start election", n.id)

	req, err := c.buildRequestVoteRequest()
	if err != nil {
		c.cancelElectionTimeoutFunc = n.scheduleOnce(n.raftConfigs.ElectionTimeoutMs, c.electAsLeader)
		return
	}
	reps := c.broadcastRequestVote(req)

	n.lock.Lock()
	defer n.lock.Unlock()

	var votes int
	for peerId, res := range reps {
		if res.Term > n.meta.GetMeta().CurrentTerm {
			peer := n.peers[peerId]
			n.transferToFollower(peer, n.raftConfigs.ElectionTimeoutMs)
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
	c.cancelElectionTimeoutFunc = n.scheduleOnce(n.raftConfigs.ElectionTimeoutMs, c.electAsLeader)
}

func (c *Candidate) buildRequestVoteRequest() (*protos.RequestVoteRequest, error) {
	n := c.node
	n.lock.Lock()
	defer n.lock.Unlock()

	meta := n.meta.GetMeta()
	meta.VoteFor = c.node.id
	meta.CurrentTerm += 1
	if err := n.meta.SaveMeta(meta); err != nil {
		return nil, err
	}

	lastLog := n.logStorage.LastEntry()
	return &protos.RequestVoteRequest{
		Term:         int64(meta.CurrentTerm),
		CandidateId:  n.id,
		LastLogIndex: lastLog.Index,
		LastLogTerm:  lastLog.Term,
	}, nil
}

func (c *Candidate) broadcastRequestVote(req *protos.RequestVoteRequest) map[string]*protos.RequestVoteResponse {
	responses := make(map[string]*protos.RequestVoteResponse)
	for peerId, peer := range c.node.peers {
		res, err := c.node.rpcClient.RequestVote(peer.Endpoint, req)
		if err != nil {
			serverLogger.Okf("request vote for peer: %s failed. %s", peerId, err)
			continue
		}
		responses[peerId] = res
	}

	return responses
}

func EndpointId(e *protos.Endpoint) string {
	return fmt.Sprintf("%s:%d", e.Ip, e.Port)
}

type PeerNode struct {
	Id       string
	Endpoint protos.Endpoint
}

func NewPeerNodes(es []protos.Endpoint) map[string]PeerNode {
	peers := make(map[string]PeerNode)
	for _, e := range es {
		id := EndpointId(&e)
		peer := PeerNode{Id: id, Endpoint: e}
		peers[id] = peer
	}
	return peers
}

type Node struct {
	id           string
	selfEndpoint protos.Endpoint
	peers        map[string]PeerNode
	state        State
	meta         MetaStorage
	logStorage   LogStorage
	commitIndex  int64
	lastApplied  int64
	scheduler    Scheduler
	raftConfigs  RaftConfigurations
	rpcClient    RpcClient
	Done         chan struct{}
	lock         sync.Mutex
}

func NewNode(configs *Configurations, rpcClient RpcClient) *Node {
	node := Node{
		id:           EndpointId(&configs.SelfEndpoint),
		selfEndpoint: configs.SelfEndpoint,
		peers:        NewPeerNodes(configs.PeerEndpoints),
		commitIndex:  -1,
		lastApplied:  -1,
		meta:         NewTestingMeta(),
		scheduler:    NewScheduler(),
		raftConfigs:  configs.RaftConfigurations,
		rpcClient:    rpcClient,
		logStorage:   &TestingLogStorage{},
		Done:         make(chan struct{}),
		lock:         sync.Mutex{},
	}

	return &node
}

func (n *Node) Start() error {
	err := n.meta.LoadMeta()
	if err != nil {
		return fmt.Errorf("load meta failed. %s", err)
	}

	n.lock.Lock()
	defer n.lock.Unlock()

	if len(n.peers) > 0 {
		initElectionTimeout := rand.Int63n(n.raftConfigs.ElectionTimeoutMs)
		n.transferToInitState(initElectionTimeout)
		return nil
	}

	n.transferToLeader()
	return nil
}

func (n *Node) scheduleOnce(timeoutMs int64, run func()) context.CancelFunc {
	ctx, cancelFunc := context.WithCancel(context.Background())
	n.scheduler.ScheduleOnce(ctx, time.Millisecond*time.Duration(timeoutMs), func() {
		if ctx.Err() != nil {
			serverLogger.Okf("scheduled job canceled. %s", ctx.Err())
			return
		}
		run()
	})
	return cancelFunc
}

func (n *Node) schedulePeriod(initialDelayMs int64, intervalMs int64, run func()) context.CancelFunc {
	ctx, cancelFunc := context.WithCancel(context.Background())
	n.scheduler.SchedulePeriod(
		ctx,
		time.Millisecond*time.Duration(initialDelayMs),
		time.Millisecond*time.Duration(intervalMs),
		func() {
			if ctx.Err() != nil {
				serverLogger.Okf("scheduled period job canceled. %s", ctx.Err())
				return
			}
			run()
		})
	return cancelFunc
}

func (n *Node) transferToLeader() {
	serverLogger.Okf("change self to leader")
	n.transferState(&Leader{node: n})
}

func (n *Node) transferToFollower(peer PeerNode, electionTimeoutMs int64) {
	serverLogger.Okf("choose %s as leader", peer.Id)
	n.transferState(&Follower{
		node:                 n,
		startElectionTimeout: electionTimeoutMs,
		leaderId:             peer.Id,
	})
}

func (n *Node) transferToInitState(electionTimeoutMs int64) {
	serverLogger.Ok("transfer to init state")
	n.transferState(&Follower{
		node:                 n,
		startElectionTimeout: electionTimeoutMs,
		leaderId:             "",
	})
}

func (n *Node) transferToCandidate() {
	n.transferState(&Candidate{node: n})
}

func (n *Node) transferState(newState State) {
	if oldState := n.state; oldState != nil {
		serverLogger.Okf("%s transfer state from %s to %s", n.id, n.state.StateType(), newState.StateType())
		oldState.Stop()
	} else {
		serverLogger.Okf("%s transfer state to %s", n.id, newState.StateType())
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
	node := NewNode(config, rpcClient)

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
}
