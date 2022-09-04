package consensus

import (
	"context"
	"fmt"
	"log"
	"math/rand"
	"sync"
	"time"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type StateType string

const (
	LeaderState    = "Leader"
	FollowerState  = "Follower"
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
	meta := l.node.meta.GetMeta()
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
}

func (l *Leader) broadcastAppendEntries() {
	heartbeat := l.buildHeartbeat()

	for peerId, peer := range l.node.peers {
		_, err := l.node.rpcClient.AppendEntries(l.node.id, peer.Endpoint, heartbeat)
		if err != nil {
			l.node.logger.Printf("append entries to peer: %s failed. %s", peerId, err)
			continue
		}
		// todo handle heartbeat response
	}

}

func (l *Leader) buildHeartbeat() *protos.AppendEntriesRequest {
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
}

func (f *Follower) Start() {
	startElectionTimeout := calculateElectionTimeout(f.node.raftConfigs)
	f.scheduleElectionTimeout(startElectionTimeout)
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
	meta := f.node.meta.GetMeta()
	f.cancelElectionTimeoutFunc()
	f.scheduleElectionTimeout(f.node.raftConfigs.ElectionTimeoutMs)
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
}

func (f *Follower) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
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

func (f *Follower) scheduleElectionTimeout(timeout int64) {
	f.cancelElectionTimeoutFunc = f.node.scheduleOnce(timeout, func() {
		// todo 收到 append entries 后不要 cancel election timeout
		// 而是等 election timeout 后检查最后一次收到心跳的时间有没有超时
		// 从而避免所有 node 最后 election timeout 的时间都一样，leader 已断开大家都在相同的时间开始 election
		f.node.transferToCandidate()
	})
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
	if c.cancelElectionTimeoutFunc != nil {
		c.cancelElectionTimeoutFunc()
		c.cancelElectionTimeoutFunc = nil
	}
}

func (_ *Candidate) StateType() StateType {
	return CandidateState
}

func (c *Candidate) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	meta := c.node.meta.GetMeta()
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
}

func (c *Candidate) HandleRequestVote(ctx context.Context, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
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
	c.node.logger.Printf("%s start election", n.id)

	req, err := c.buildRequestVoteRequest()
	if err != nil {
		timeout := calculateElectionTimeout(n.raftConfigs)
		c.cancelElectionTimeoutFunc = n.scheduleOnce(timeout, c.electAsLeader)
		return
	}
	reps := c.broadcastRequestVote(req)

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
	if votes >= (len(n.peers)+1)/2 {
		n.transferToLeader()
		return
	}

	c.node.logger.Printf("%s elect as leader failed, try elect leader later", n.id)
	initElectionTimeout := calculateElectionTimeout(n.raftConfigs)
	c.cancelElectionTimeoutFunc = n.scheduleOnce(initElectionTimeout, c.electAsLeader)
}

func (c *Candidate) buildRequestVoteRequest() (*protos.RequestVoteRequest, error) {
	n := c.node

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
	type BroadcastResponse struct {
		peerId string
		response *protos.RequestVoteResponse
		err error
	}
	responseChan := make(chan BroadcastResponse, len(c.node.peers))
	for peerId, peer := range c.node.peers {
		go func() {
			res, err := c.node.rpcClient.RequestVote(c.node.id, peer.Endpoint, req)
			responseChan <- BroadcastResponse{peerId: peerId, response: res, err: err}
		}()
	}

	responses := make(map[string]*protos.RequestVoteResponse)
	for res := range responseChan {
		if res.err != nil {
			c.node.logger.Printf("request vote for peer: %s failed. %s", res.peerId, res.err)
			continue
		}
		responses[res.peerId] = res.response
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
		id := e.NodeId
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
	logger       *log.Logger
}

func NewNode(configs *Configurations, rpcService RpcService, logger *log.Logger) *Node {
	nodeLogger := log.New(logger.Writer(), fmt.Sprintf("[Node-%s]", configs.SelfEndpoint.NodeId), logger.Flags())
	rpcClient := rpcService.GetRpcClient()
	node := Node{
		id:           configs.SelfEndpoint.NodeId,
		selfEndpoint: configs.SelfEndpoint,
		peers:        NewPeerNodes(configs.PeerEndpoints),
		commitIndex:  -1,
		lastApplied:  -1,
		meta:         NewTestingMeta(configs.SelfEndpoint.NodeId, logger),
		scheduler:    NewScheduler(),
		raftConfigs:  configs.RaftConfigurations,
		rpcClient:    rpcClient,
		logStorage:   &TestingLogStorage{},
		Done:         make(chan struct{}),
		lock:         sync.Mutex{},
		logger:       nodeLogger,
	}
	if err := rpcService.RegisterRpcHandler(node.id, &node); err != nil {
		logger.Fatalf("register node: %s to rpc service failed: %s", node.id, err)
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
		initElectionTimeout := calculateElectionTimeout(n.raftConfigs)
		n.transferToInitState(initElectionTimeout)
		return nil
	}

	n.transferToLeader()
	return nil
}

func (n *Node) HandleRequestVote(ctx context.Context, fromNodeId string, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	if !n.lock.TryLock() {
		return nil, fmt.Errorf("node is busy, retry later")
	}
	defer n.lock.Unlock()
	if _, ok := n.peers[fromNodeId]; !ok {
		return nil, fmt.Errorf("unknown peer node: %s", fromNodeId)
	}
	return n.state.HandleRequestVote(ctx, request)
}

func (n *Node) HandleAppendEntries(ctx context.Context, fromNodeId string, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	if !n.lock.TryLock() {
		return nil, fmt.Errorf("node is busy, retry later")
	}
	defer n.lock.Unlock()
	if _, ok := n.peers[fromNodeId]; !ok {
		return nil, fmt.Errorf("unknown peer node: %s", fromNodeId)
	}
	return n.state.HandleAppendEntries(ctx, request)
}

func (n *Node) scheduleOnce(timeoutMs int64, run func()) context.CancelFunc {
	ctx, cancelFunc := context.WithCancel(context.Background())
	n.scheduler.ScheduleOnce(ctx, time.Millisecond*time.Duration(timeoutMs), func() {
		if ctx.Err() != nil {
			n.logger.Printf("scheduled job canceled. %s", ctx.Err())
			return
		}
		n.lock.Lock()
		defer n.lock.Unlock()
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
				n.logger.Printf("scheduled period job canceled. %s", ctx.Err())
				return
			}
			n.lock.Lock()
			defer n.lock.Unlock()
			run()
		})
	return cancelFunc
}

func (n *Node) transferToLeader() {
	n.transferState(&Leader{node: n})
}

func (n *Node) transferToFollower(peer PeerNode, electionTimeoutMs int64) {
	n.logger.Printf("choose %s as leader", peer.Id)
	n.transferState(&Follower{
		node:     n,
		leaderId: peer.Id,
	})
}

func (n *Node) transferToInitState(electionTimeoutMs int64) {
	n.transferState(&Follower{
		node:     n,
		leaderId: "",
	})
}

func (n *Node) transferToCandidate() {
	n.transferState(&Candidate{node: n})
}

func (n *Node) transferState(newState State) {
	if oldState := n.state; oldState != nil {
		n.logger.Printf("%s transfer state from %s to %s", n.id, n.state.StateType(), newState.StateType())
		oldState.Stop()
	} else {
		n.logger.Printf("%s transfer state to %s", n.id, newState.StateType())
	}
	n.state = newState
	n.state.Start()
}

func calculateElectionTimeout(config RaftConfigurations) int64 {
	for {
		timeout := rand.Int63n(config.ElectionTimeoutMs)
		if timeout > config.PingTimeoutMs {
			return timeout
		}
	}
}
