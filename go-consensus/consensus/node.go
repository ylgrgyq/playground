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

type NodeStatus string

const (
	Init    = "Init"
	Started = "Started"
	Stopped = "Stopped"
)

type StateType string

const (
	LeaderState    = "Leader"
	FollowerState  = "Follower"
	CandidateState = "Candidate"
)

type Ballot[T any] struct {
	peers   map[string]*PeerNode
	vote    int
	voteVal T
}

func (b *Ballot[T]) Count(peerNodeId string) {
	if _, ok := b.peers[peerNodeId]; ok {
		b.vote += 1
	}
}

func (b *Ballot[T]) Pass() bool {
	return b.vote >= (len(b.peers)+1)/2
}

type State interface {
	Start()
	StateType() StateType
	Append(bytes []byte) (*AppendResponse, error)
	HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error)
	UpdateAppliedLogIndex(index int64)
	Stop()
}

type Leader struct {
	node                  *Node
	cancelPingTimeoutFunc context.CancelFunc
	pendingAppend         []*AppendResponse
	ballots               []*Ballot[int64]
}

func (l *Leader) Start() {
	for _, peer := range l.node.peers {
		peer.initialize(l.node.logStorage)
	}

	meta := l.node.meta.GetMeta()
	lastEntry := l.node.logStorage.LastEntry()
	initEntry := protos.LogEntry{Term: meta.CurrentTerm, Index: lastEntry.Index + 1, Data: []byte{}}
	if err := l.node.logStorage.Append(&initEntry); err != nil {
		l.node.logger.Printf("append init entry as leader failed. error: %s", err)
		l.node.transferToFollower()
		return
	}

	l.cancelPingTimeoutFunc = l.node.schedulePeriod(
		0,
		l.node.raftConfigs.PingTimeoutMs,
		func() {
			for _, peer := range l.node.peers {
				l.sendHeartbeat(peer)
			}
		})
}

func (l *Leader) Stop() {
	if l.cancelPingTimeoutFunc != nil {
		l.cancelPingTimeoutFunc()
		l.cancelPingTimeoutFunc = nil
	}
	for _, resp := range l.pendingAppend {
		err := fmt.Errorf("leader step down")
		resp.Done <- err
	}
}

func (*Leader) StateType() StateType {
	return LeaderState
}

func (l *Leader) Append(bytes []byte) (*AppendResponse, error) {
	lastEntry := l.node.logStorage.LastEntry()
	meta := l.node.meta.GetMeta()
	entry := protos.LogEntry{Term: meta.CurrentTerm, Index: lastEntry.Index + 1, Data: bytes}
	if err := l.node.logStorage.Append(&entry); err != nil {
		return nil, fmt.Errorf("append log failed. error: %s", err)
	}

	resp := AppendResponse{Index: entry.Index, Done: make(chan error)}
	l.pendingAppend = append(l.pendingAppend, &resp)
	l.ballots = append(l.ballots, &Ballot[int64]{peers: l.node.peers, voteVal: entry.Index})

	for _, peer := range l.node.peers {
		l.sendAppend(peer)
	}
	return nil, nil
}

func (l *Leader) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	meta := l.node.meta.GetMeta()
	l.node.logger.Printf("leader: %s receive append entries request from: %s with term %d.", l.node.Id, request.LeaderId, request.Term)
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: false}, nil
}

func (l *Leader) UpdateAppliedLogIndex(index int64) {
	for _, resp := range l.pendingAppend {
		if index >= resp.Index {
			close(resp.Done)
		}
	}
}

func (l *Leader) ballotAndUpdateCommitIndex() {
	ballots := make(map[int64]*Ballot[int64])
	for peerId, peer := range l.node.peers {
		ballot, ok := ballots[peer.matchIndex]
		if !ok {
			ballot = &Ballot[int64]{peers: l.node.peers}
			ballots[peer.matchIndex] = ballot
		}
		ballot.Count(peerId)
	}
	for index, ballot := range ballots {
		if ballot.Pass() {
			l.node.updateCommitIndex(index)
			return
		}
	}
}

func (l *Leader) sendHeartbeat(peer *PeerNode) {
	if peer.pendingAppend != 0 {
		return
	}

	heartbeat := l.buildHeartbeat(peer)
	peer.pendingAppend += 1
	res, err := l.node.rpcClient.AppendEntries(l.node.Id, peer.Endpoint, heartbeat)
	peer.pendingAppend -= 1
	if err != nil {
		l.node.logger.Printf("heartbeat for peer: %s failed. %s", peer.Id, err)
		return
	}

	if !res.Success {
		peer.nextIndex = heartbeat.PrevLogIndex
		l.sendProbe(peer)
		return
	}
	l.sendAppend(peer)
}

func (l *Leader) sendAppend(peer *PeerNode) {
	if peer.pendingAppend != 0 {
		return
	}

	append := l.buildAppendEntries(peer)
	if append == nil {
		return
	}

	peer.pendingAppend += 1
	res, err := l.node.rpcClient.AppendEntries(l.node.Id, peer.Endpoint, append)
	peer.pendingAppend -= 1
	if err != nil {
		l.node.logger.Printf("heartbeat for peer: %s failed. %s", peer.Id, err)
		return
	}

	if !res.Success {
		peer.nextIndex = append.PrevLogIndex
		l.sendProbe(peer)
		return
	}

	lastIndex := append.Entries[len(append.Entries)-1].Index
	if lastIndex > peer.matchIndex {
		peer.matchIndex = lastIndex
		peer.nextIndex = lastIndex + 1
	}

	l.sendAppend(peer)
}

func (l *Leader) sendProbe(peer *PeerNode) {
	if peer.pendingAppend != 0 {
		return
	}

	heartbeat := l.buildHeartbeat(peer)
	peer.pendingAppend += 1
	res, err := l.node.rpcClient.AppendEntries(l.node.Id, peer.Endpoint, heartbeat)
	peer.pendingAppend -= 1
	if err != nil {
		l.node.logger.Printf("heartbeat for peer: %s failed. %s", peer.Id, err)
		return
	}

	if !res.Success {
		peer.nextIndex = heartbeat.PrevLogIndex
		l.sendProbe(peer)
		return
	}
	l.sendAppend(peer)
}

func (l *Leader) buildAppendEntries(peer *PeerNode) *protos.AppendEntriesRequest {
	lastEntry := l.node.logStorage.LastEntry()
	if peer.nextIndex > lastEntry.Index {
		l.node.logger.Printf("peer: %s, has all log entries.", peer.Id)
		return nil
	}

	meta := l.node.meta.GetMeta()
	nextIndex := peer.nextIndex
	prevLog, err := l.node.logStorage.GetEntry(nextIndex - 1)
	if err != nil {
		l.node.logger.Printf("get prev log with index: %d for peer: %s failed. error: %s", nextIndex-1, peer.Id, err)
		return nil
	}
	entries, err := l.node.logStorage.GetEntries(nextIndex)
	if err != nil {
		l.node.logger.Printf("get log entries log with index: %d for peer: %s failed. error: %s", nextIndex, peer.Id, err)
		return nil
	}

	if len(entries) == 0 {
		l.node.logger.Fatalf("get empty entries for peer: %s. nextIndex: %d, lastEntryIndex: %d", peer.Id, nextIndex, lastEntry.Index)
	}

	return &protos.AppendEntriesRequest{
		Term:         int64(meta.CurrentTerm),
		LeaderId:     l.node.Id,
		PrevLogIndex: prevLog.Index,
		PrevLogTerm:  prevLog.Term,
		Entries:      entries,
		LeaderCommit: l.node.commitIndex,
	}
}

func (l *Leader) buildHeartbeat(peer *PeerNode) *protos.AppendEntriesRequest {
	meta := l.node.meta.GetMeta()

	nextIndex := peer.nextIndex
	prevLog, err := l.node.logStorage.GetEntry(nextIndex - 1)
	if err != nil {
		l.node.logger.Printf("get prev log with index: %d for peer: %s failed. error: %s", nextIndex-1, peer.Id, err)
		return nil
	}

	return &protos.AppendEntriesRequest{
		Term:         int64(meta.CurrentTerm),
		LeaderId:     l.node.Id,
		PrevLogIndex: prevLog.Index,
		PrevLogTerm:  prevLog.Term,
		Entries:      []*protos.LogEntry{},
		LeaderCommit: l.node.commitIndex,
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

func (f *Follower) Append(bytes []byte) (*AppendResponse, error) {
	return nil, fmt.Errorf("node is not leader")
}

func (f *Follower) UpdateAppliedLogIndex(index int64) {

}

func (f *Follower) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	meta := f.node.meta.GetMeta()
	if meta.VoteFor != request.LeaderId {
		return nil, fmt.Errorf("receive append entries from leader: %s which I'm not voted for", request.LeaderId)
	}
	f.cancelElectionTimeoutFunc()
	f.scheduleElectionTimeout(f.node.raftConfigs.ElectionTimeoutMs)

	if len(request.Entries) == 0 {
		f.node.updateCommitIndex(request.LeaderCommit)
		return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
	}

	ok, err := f.node.logStorage.AppendEntries(request.PrevLogTerm, request.PrevLogIndex, request.Entries)
	if err != nil {
		return nil, fmt.Errorf("append entries failed. error: %s", err)
	}
	if !ok {
		f.node.logger.Printf("append entries failed due to prev log not match. prevTermInRequest: %d, prevIndexInRequest: %d",
			request.PrevLogTerm, request.PrevLogIndex)
		return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: false}, nil
	}
	f.node.updateCommitIndex(request.LeaderCommit)
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: true}, nil
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

func (c *Candidate) Append(bytes []byte) (*AppendResponse, error) {
	return nil, fmt.Errorf("node is not leader")
}

func (*Candidate) StateType() StateType {
	return CandidateState
}

func (c *Candidate) HandleAppendEntries(ctx context.Context, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	meta := c.node.meta.GetMeta()
	c.node.logger.Printf("candidate: %s receive append entries request from: %s with term %d.", c.node.Id, request.LeaderId, request.Term)
	return &protos.AppendEntriesResponse{Term: meta.CurrentTerm, Success: false}, nil
}

func (*Candidate) UpdateAppliedLogIndex(index int64) {

}

func (c *Candidate) electAsLeader() {
	n := c.node
	c.node.logger.Printf("%s start election", n.Id)

	req, err := c.buildRequestVoteRequest()
	if err == nil {
		reps := c.broadcastRequestVote(req)
		if c.node.state != c {
			return
		}
		ballot := Ballot[string]{peers: c.node.peers, voteVal: n.Id}
		for peerId, res := range reps {
			if res.Term > n.meta.GetMeta().CurrentTerm {
				n.transferToFollower()
				return
			}

			if res.VoteGranted {
				ballot.Count(peerId)
			}
		}
		if ballot.Pass() {
			n.transferToLeader()
			return
		} else {
			c.node.logger.Printf("%s elect as leader failed due to ballot not fulfil", n.Id)
		}
	} else {
		c.node.logger.Printf("%s elect as leader failed due to error: %s", n.Id, err)
	}

	timeout := calculateElectionTimeout(n.raftConfigs)
	c.cancelElectionTimeoutFunc = n.scheduleOnce(timeout, c.electAsLeader)
}

func (c *Candidate) buildRequestVoteRequest() (*protos.RequestVoteRequest, error) {
	n := c.node

	meta := n.meta.GetMeta()
	meta.VoteFor = c.node.Id
	meta.CurrentTerm += 1
	if err := n.meta.SaveMeta(meta.CurrentTerm, meta.VoteFor); err != nil {
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
	Id            string
	Endpoint      *protos.Endpoint
	nextIndex     int64
	matchIndex    int64
	pendingAppend int32
}

func NewPeerNodes(es []*protos.Endpoint) map[string]*PeerNode {
	peers := make(map[string]*PeerNode)
	for _, e := range es {
		id := e.NodeId
		peer := PeerNode{Id: id, Endpoint: e}
		peers[id] = &peer
	}
	return peers
}

func (p *PeerNode) initialize(storage LogStorage) {
	lastEntry := storage.LastEntry()
	p.nextIndex = lastEntry.Index + 1
	p.matchIndex = 0
}

type AppendResponse struct {
	Index int64
	Done  chan error
}

type Node struct {
	Id                   string
	Done                 chan struct{}
	ApplyLogChan         chan []*protos.LogEntry
	status               NodeStatus
	selfEndpoint         *protos.Endpoint
	peers                map[string]*PeerNode
	state                State
	meta                 MetaStorage
	logStorage           LogStorage
	commitIndex          int64
	lastApplied          int64
	scheduler            Scheduler
	raftConfigs          RaftConfigurations
	rpcClient            RpcClient
	lock                 sync.Mutex
	logger               *log.Logger
	rpcServiceUnregister RpcServiceHandlerUnregister
}

func NewNode(configs *Configurations, rpcService RpcService, applyLogChan chan []*protos.LogEntry, logger *log.Logger) *Node {
	nodeLogger := log.New(logger.Writer(), fmt.Sprintf("[Node-%s]", configs.SelfEndpoint.NodeId), logger.Flags())
	rpcClient := rpcService.GetRpcClient()
	node := Node{
		Id:           configs.SelfEndpoint.NodeId,
		ApplyLogChan: applyLogChan,
		status:       Init,
		selfEndpoint: configs.SelfEndpoint,
		peers:        NewPeerNodes(configs.PeerEndpoints),
		commitIndex:  0,
		lastApplied:  0,
		meta:         NewMeta(configs, logger),
		scheduler:    NewScheduler(),
		raftConfigs:  configs.RaftConfigurations,
		rpcClient:    rpcClient,
		logStorage:   NewLogStorage(),
		Done:         make(chan struct{}),
		lock:         sync.Mutex{},
		logger:       nodeLogger,
	}
	unregister, err := rpcService.RegisterRpcHandler(node.Id, &node)
	if err != nil {
		logger.Fatalf("register node: %s to rpc service failed: %s", node.Id, err)
	}
	node.rpcServiceUnregister = unregister
	return &node
}

func (n *Node) Start() error {
	n.lock.Lock()
	defer n.lock.Unlock()

	if n.status != Init {
		return fmt.Errorf("can not start Node in %s status", n.status)
	}

	err := n.meta.LoadMeta()
	if err != nil {
		return fmt.Errorf("load meta failed. %s", err)
	}

	err = n.logStorage.Start()
	if err != nil {
		return fmt.Errorf("start log storage failed. %s", err)
	}

	if len(n.peers) > 0 {
		n.transferToFollower()
		n.status = Started
		return nil
	}

	n.transferToLeader()
	n.status = Started
	return nil
}

func (n *Node) Stop(ctx context.Context) {
	if n.status == Stopped {
		return
	}
	n.lock.Lock()
	defer n.lock.Unlock()

	if n.status == Stopped {
		return
	}

	n.status = Stopped

	n.rpcServiceUnregister()
	n.state.Stop()
	n.state = nil
	n.logStorage.Stop(ctx)
	n.meta.Stop(ctx)

	close(n.Done)
}

func (n *Node) Append(bytes []byte) (*AppendResponse, error) {
	n.lock.Lock()
	defer n.lock.Unlock()

	if n.status != Started {
		return nil, fmt.Errorf("node in invalid status: %s", n.status)
	}

	resp, err := n.state.Append(bytes)
	if err != nil {
		return nil, err
	}

	return resp, nil
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
	if err := n.meta.SaveMeta(reqTerm, request.CandidateId); err != nil {
		return nil, err
	}
	n.transferToFollowerWithLeader(peer)
	return &protos.RequestVoteResponse{
		Term:        int64(reqTerm),
		VoteGranted: true,
	}, nil
}

func (n *Node) HandleAppendEntries(ctx context.Context, fromNodeId string, request *protos.AppendEntriesRequest) (*protos.AppendEntriesResponse, error) {
	n.lock.Lock()
	defer n.lock.Unlock()
	peer, ok := n.peers[fromNodeId]
	if !ok {
		return nil, fmt.Errorf("unknown peer node: %s", fromNodeId)
	}

	meta := n.meta.GetMeta()
	if request.Term < meta.CurrentTerm {
		return &protos.AppendEntriesResponse{
			Term:    int64(meta.CurrentTerm),
			Success: false,
		}, nil
	}

	if request.Term > meta.CurrentTerm {
		if err := n.meta.SaveMeta(request.Term, fromNodeId); err != nil {
			return nil, err
		}
		n.transferToFollowerWithLeader(peer)
	}

	return n.state.HandleAppendEntries(ctx, request)
}

func (n *Node) UpdateAppliedLogIndex(index int64) {
	n.lock.Lock()
	defer n.lock.Unlock()

	if index > n.lastApplied {
		n.lastApplied = index
	}
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

func (n *Node) updateCommitIndex(commitIndex int64) {
	if commitIndex > n.commitIndex {
		n.logger.Printf("update commit index to: %d", commitIndex)
		n.commitIndex = commitIndex

	} else {
		n.logger.Printf("skip update commit index to: %d, currentCommitIndex: %d", commitIndex, n.commitIndex)
	}
}

func calculateElectionTimeout(config RaftConfigurations) int64 {
	for {
		timeout := rand.Int63n(config.ElectionTimeoutMs)
		if timeout > config.PingTimeoutMs {
			return timeout
		}
	}
}