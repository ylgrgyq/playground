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

type Ballot struct {
	peers map[string]*PeerNode
	vote  int
}

func (b *Ballot) CountVoteFrom(peerNodeId string) {
	if _, ok := b.peers[peerNodeId]; ok {
		b.vote += 1
	}
}

func (b *Ballot) Pass() bool {
	return b.vote >= (len(b.peers)+1)/2
}

type State interface {
	Start()
	StateType() StateType
	HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
	Stop()
}

type Leader struct {
	node                  *Node
	cancelPingTimeoutFunc context.CancelFunc
	scheduler             Scheduler
}

func (l *Leader) Start() {
	for _, peer := range l.node.peers {
		peer.initialize(l.node.logStorage)
	}
	l.cancelPingTimeoutFunc = l.node.schedulePeriod(
		0,
		l.node.raftConfigs.PingTimeoutMs,
		func() {
			l.node.lock.Lock()
			defer l.node.lock.Unlock()
			heartbeats := make(map[string]*protos.AppendEntriesRequest)
			for _, peer := range l.node.peers {
				heartbeats[peer.Id] = l.buildAppendEntries(peer)
			}
			resps := l.broadcastAppendEntries(heartbeats)
			if l.node.state != l {
				return
			}
			meta := l.node.meta.GetMeta()
			for peerId, resp := range resps {
				peer := l.node.peers[peerId]
				req := heartbeats[peerId]
				if resp.Term > meta.CurrentTerm {
					l.node.transferToFollower()
					return
				}
				l.handleAppendEntriesResponse(peer, req, resp)
			}
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

func (l *Leader) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	meta := l.node.meta.GetMeta()
	l.node.logger.Printf("leader: %s receive append entries request from: %s with term %d.", l.node.Id, request.LeaderId, request.Term)
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: false}, nil
}

func (l *Leader) broadcastAppendEntries(requests map[string]*protos.AppendEntriesRequest) map[string]*protos.AppendEntriesResponse {
	l.node.lock.Unlock()
	defer l.node.lock.Lock()
	type BroadcastResponse struct {
		peerId   string
		response *protos.AppendEntriesResponse
		err      error
	}
	group := sync.WaitGroup{}
	group.Add(len(l.node.peers))
	responseChan := make(chan BroadcastResponse, len(l.node.peers))
	for peerId, req := range requests{
		go func(peerId string, req *protos.AppendEntriesRequest) {
			defer group.Done()
			peer := l.node.peers[peerId]
			res, err := l.node.rpcClient.AppendEntries(l.node.Id, peer.Endpoint, req)
			responseChan <- BroadcastResponse{peerId: peerId, response: res, err: err}
		}(peerId, req)
	}

	go func() {
		group.Wait()
		close(responseChan)
	}()

	responses := make(map[string]*protos.AppendEntriesResponse)
	for res := range responseChan {
		if res.err != nil {
			l.node.logger.Printf("append entries request for peer: %s failed. %s", res.peerId, res.err)
			continue
		}
		responses[res.peerId] = res.response
	}
	return responses
}

func (l *Leader) buildAppendEntries(peer *PeerNode) *protos.AppendEntriesRequest {
	meta := l.node.meta.GetMeta()

	nextIndex := peer.nextIndex
	prevLog := l.node.logStorage.GetEntry(nextIndex - 1)
	entries := l.node.logStorage.GetEntries(nextIndex)

	return &protos.AppendEntriesRequest{
		Term:         int64(meta.CurrentTerm),
		LeaderId:     l.node.Id,
		PrevLogIndex: prevLog.Index,
		PrevLogTerm:  prevLog.Term,
		Entries:      entries,
		LeaderCommit: l.node.commitIndex,
	}
}

func (l *Leader) handleAppendEntriesResponse(peer *PeerNode, req *protos.AppendEntriesRequest, resp *protos.AppendEntriesResponse)  {
    if resp.Success {
    	if len(req.Entries) > 0 {
			lastEntry := req.Entries[len(req.Entries) - 1]
			peer.matchIndex = lastEntry.Index
			peer.nextIndex = lastEntry.Index + 1
		}
    	return
	}

	if len(req.Entries) == 0 {
		l.node.logger.Printf("append empty entries failed on peer: %s. prevIndex: %d", peer.Id, req.PrevLogIndex)
		return
	}

    peer.nextIndex = peer.nextIndex - 1
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

func (f *Follower) scheduleElectionTimeout(timeout int64) {
	f.cancelElectionTimeoutFunc = f.node.scheduleOnce(timeout, func() {
		f.node.lock.Lock()
		defer f.node.lock.Unlock()
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
	c.node.logger.Printf("candidate: %s receive append entries request from: %s with term %d.", c.node.Id, request.LeaderId, request.Term)
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: false}, nil
}

func (c *Candidate) electAsLeader() {
	c.node.lock.Lock()
	defer c.node.lock.Unlock()

	n := c.node
	c.node.logger.Printf("%s start election", n.Id)

	req, err := c.buildRequestVoteRequest()
	if err == nil {
		reps := c.broadcastRequestVote(req)
		if c.node.state != c {
			return
		}
		ballot := Ballot{peers: c.node.peers}
		for peerId, res := range reps {
			if res.Term > n.meta.GetMeta().CurrentTerm {
				n.transferToFollower()
				return
			}

			if res.VoteGranted {
				ballot.CountVoteFrom(peerId)
			}
		}
		if ballot.Pass() {
			n.transferToLeader()
			return
		}
	}

	c.node.logger.Printf("%s elect as leader failed, try elect leader later. error :%s", n.Id, err)
	timeout := calculateElectionTimeout(n.raftConfigs)
	c.cancelElectionTimeoutFunc = n.scheduleOnce(timeout, c.electAsLeader)
}

func (c *Candidate) buildRequestVoteRequest() (*protos.RequestVoteRequest, error) {
	n := c.node

	meta := n.meta.GetMeta()
	meta.VoteFor = c.node.Id
	meta.CurrentTerm += 1
	if err := n.meta.SaveMeta(meta); err != nil {
		return nil, err
	}

	lastLog := n.logStorage.LastEntry()
	return &protos.RequestVoteRequest{
		Term:         int64(meta.CurrentTerm),
		CandidateId:  n.Id,
		LastLogIndex: lastLog.Index,
		LastLogTerm:  lastLog.Term,
	}, nil
}

func (c *Candidate) broadcastRequestVote(req *protos.RequestVoteRequest) map[string]*protos.RequestVoteResponse {
	c.node.lock.Unlock()
	defer c.node.lock.Lock()
	type BroadcastResponse struct {
		peerId   string
		response *protos.RequestVoteResponse
		err      error
	}
	group := sync.WaitGroup{}
	group.Add(len(c.node.peers))
	responseChan := make(chan BroadcastResponse, len(c.node.peers))
	for peerId, peer := range c.node.peers {
		go func(peerId string, peer *PeerNode) {
			defer group.Done()
			res, err := c.node.rpcClient.RequestVote(c.node.Id, peer.Endpoint, req)
			responseChan <- BroadcastResponse{peerId: peerId, response: res, err: err}
		}(peerId, peer)
	}

	go func() {
		group.Wait()
		close(responseChan)
	}()

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

type PeerNode struct {
	Id         string
	Endpoint   protos.Endpoint
	nextIndex  int64
	matchIndex int64
}

func NewPeerNodes(es []protos.Endpoint) map[string]*PeerNode {
	peers := make(map[string]*PeerNode)
	for _, e := range es {
		id := e.NodeId
		peer := PeerNode{Id: id, Endpoint: e, nextIndex: 1, matchIndex: 0}
		peers[id] = &peer
	}
	return peers
}

func (p *PeerNode) initialize(storage LogStorage) {
	lastEntry := storage.LastEntry()
	p.nextIndex = lastEntry.Index
	p.matchIndex = 0
}

type Node struct {
	Id           string
	selfEndpoint protos.Endpoint
	peers        map[string]*PeerNode
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
		Id:           configs.SelfEndpoint.NodeId,
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
	if err := rpcService.RegisterRpcHandler(node.Id, &node); err != nil {
		logger.Fatalf("register node: %s to rpc service failed: %s", node.Id, err)
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
		n.transferToFollower()
		return nil
	}

	n.transferToLeader()
	return nil
}

func (n *Node) HandleRequestVote(ctx context.Context, fromNodeId string, request *protos.RequestVoteRequest) (*protos.RequestVoteResponse, error) {
	n.lock.Lock()
	defer n.lock.Unlock()
	if _, ok := n.peers[fromNodeId]; !ok {
		return nil, fmt.Errorf("unknown peer node: %s", fromNodeId)
	}
	meta := n.meta.GetMeta()
	reqTerm := request.Term
	if reqTerm < meta.CurrentTerm {
		return &protos.RequestVoteResponse{
			Term:        int64(meta.CurrentTerm),
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

		if !n.logStorage.IsAtLeastUpToDateThanMe(request.LastLogTerm, request.LastLogIndex) {
			return &protos.RequestVoteResponse{
				Term:        meta.CurrentTerm,
				VoteGranted: false,
			}, nil
		}
	}

	peer := n.peers[request.CandidateId]
	meta = Meta{CurrentTerm: reqTerm, VoteFor: request.CandidateId}
	if err := n.meta.SaveMeta(meta); err != nil {
		return nil, err
	}
	n.transferToFollowerWithLeader(peer)
	return &protos.RequestVoteResponse{
		Term:        int64(meta.CurrentTerm),
		VoteGranted: true,
	}, nil
}

func (n *Node) HandleAppendEntries(ctx context.Context, fromNodeId string, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	n.lock.Lock()
	defer n.lock.Unlock()
	peer, ok := n.peers[fromNodeId];
	if !ok {
		return nil, fmt.Errorf("unknown peer node: %s", fromNodeId)
	}

	meta := n.meta.GetMeta()
	if request.Term < meta.CurrentTerm {
		return &protos.AppendEntriesResponse{
			Term:        int64(meta.CurrentTerm),
			Success: false,
		}, nil
	}

	if request.Term > meta.CurrentTerm {
		meta = Meta{CurrentTerm: request.Term, VoteFor: fromNodeId}
		if err := n.meta.SaveMeta(meta); err != nil {
			return nil, err
		}
		n.transferToFollowerWithLeader(peer)
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

func (n *Node) transferToFollowerWithLeader(leader *PeerNode) {
	n.logger.Printf("choose %s as leader", leader.Id)
	n.transferState(&Follower{
		node:     n,
		leaderId: leader.Id,
	})
}

func (n *Node) transferToFollower() {
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
		n.logger.Printf("%s transfer state from %s to %s", n.Id, n.state.StateType(), newState.StateType())
		oldState.Stop()
	} else {
		n.logger.Printf("%s transfer state to %s", n.Id, newState.StateType())
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
