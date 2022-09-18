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
	knownEndpointIds map[string]bool
	vote             int
	valueToVote      T
}

func NewBallot[T any](selfId string, peers map[string]*PeerNode, valToVote T) *Ballot[T] {
	knownPeerIds := make(map[string]bool)
	knownPeerIds[selfId] = true
	for peerId := range peers {
		knownPeerIds[peerId] = true
	}
	return &Ballot[T]{knownPeerIds, 0, valToVote}
}

func (b *Ballot[T]) Count(peerNodeId string) int {
	if _, ok := b.knownEndpointIds[peerNodeId]; ok {
		b.vote += 1
	}
	return b.vote
}

func (b *Ballot[T]) Pass() bool {
	return b.vote >= (len(b.knownEndpointIds)+1)/2
}

type AppendEntriesBallots struct {
	ballots           []*Ballot[int64]
	firstIndex        int64
	lastIndex         int64
	latestCommitIndex int64
	node              *Node
}

func NewAppendEntriesBollots(n *Node) *AppendEntriesBallots {
	return &AppendEntriesBallots{[]*Ballot[int64]{}, 0, 0, 0, n}
}

func (a *AppendEntriesBallots) AppendPendingEntry(entry *protos.LogEntry) {
	a.ballots = append(a.ballots, NewBallot(a.node.Id, a.node.peers, entry.Index))
	if len(a.ballots) == 1 {
		a.firstIndex = entry.Index
	}
}

func (a *AppendEntriesBallots) UpdateBallot(nodeId string, matchIndex int64) int64 {
	for _, ballot := range a.ballots {
		if matchIndex < ballot.valueToVote {
			break
		}
		votes := ballot.Count(nodeId)
		a.node.logger.Printf("update ballot votes to %d for index: %d by: %s", votes, matchIndex, nodeId)
		if !ballot.Pass() {
			continue
		}

		if matchIndex > a.latestCommitIndex {
			a.latestCommitIndex = matchIndex
		}
	}

	if a.latestCommitIndex >= a.firstIndex {
		a.ballots = a.ballots[a.latestCommitIndex-a.firstIndex:]
		a.firstIndex = 0
		if len(a.ballots) > 0 {
			a.firstIndex = a.ballots[0].valueToVote
		}
	}
	return a.latestCommitIndex
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
	ballots               *AppendEntriesBallots
}

func (l *Leader) Start() {
	for _, peer := range l.node.peers {
		peer.initialize(l.node.logStorage)
	}

	if _, err := l.Append([]byte{}); err != nil {
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
	for _, peer := range l.node.peers {
		peer.flyingAppendMid = ""
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
	l.ballots.AppendPendingEntry(&entry)
	l.ballots.UpdateBallot(l.node.Id, entry.Index)
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

func (l *Leader) sendHeartbeat(peer *PeerNode) {
	if len(peer.flyingAppendMid) != 0 {
		return
	}

	heartbeat := l.buildHeartbeat(peer)
	l.handleAppendEntriesResponse(heartbeat, peer)
}

func (l *Leader) sendAppend(peer *PeerNode) {
	if len(peer.flyingAppendMid) != 0 {
		return
	}

	append := l.buildAppendEntries(peer)
	if append == nil {
		return
	}

	l.handleAppendEntriesResponse(append, peer)
}

func (l *Leader) buildAppendEntries(peer *PeerNode) *protos.AppendEntriesRequest {
	lastEntry := l.node.logStorage.LastEntry()
	if peer.nextIndex > lastEntry.Index {
		l.node.logger.Printf("peer: %s, has all log entries. lastIndex: %d, nextIndex: %d.", peer.Id, lastEntry.Index, peer.nextIndex)
		return nil
	}

	meta := l.node.meta.GetMeta()
	nextIndex := peer.nextIndex
	prevLog, ok := l.node.logStorage.GetEntry(nextIndex - 1)
	if !ok {
		l.node.logger.Printf("get prev log with index: %d for peer: %s not found", nextIndex-1, peer.Id)
		return nil
	}
	entries, err := l.node.logStorage.GetEntriesFromIndex(nextIndex)
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
	prevLog, ok := l.node.logStorage.GetEntry(nextIndex - 1)
	if !ok {
		l.node.logger.Printf("get prev log with index: %d for peer: %s not found", nextIndex-1, peer.Id)
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

func (l *Leader) handleAppendEntriesResponse(
	append *protos.AppendEntriesRequest,
	peer *PeerNode,
) {
	mid, resChan := l.node.rpcClient.AppendEntries(l.node.Id, peer.Endpoint, append)
	peer.flyingAppendMid = mid
	go func() {
		res := <-resChan
		l.node.lock.Lock()
		defer l.node.lock.Unlock()
		if l.node.state != l {
			return
		}
		if peer.flyingAppendMid != mid {
			return
		}
		peer.flyingAppendMid = ""
		if res.err != nil {
			l.node.logger.Printf("append for peer: %s failed. %s", peer.Id, res.err)
			return
		}

		if !res.response.Success {
			meta := l.node.meta.GetMeta()
			if res.response.Term > meta.CurrentTerm {
				l.node.transferToFollower()
				return
			}
			peer.nextIndex = append.PrevLogIndex
			l.sendHeartbeat(peer)
			return
		}
		if len(append.Entries) > 0 {
			lastIndex := append.Entries[len(append.Entries)-1].Index
			if lastIndex > peer.matchIndex {
				peer.matchIndex = lastIndex
				peer.nextIndex = lastIndex + 1
				commitIndex := l.ballots.UpdateBallot(peer.Id, lastIndex)
				l.node.logger.Printf("update peer: %s. matchIndex: %d, nextIndex: %d, commitIndex: %d", peer.Id, peer.matchIndex, peer.nextIndex, commitIndex)
				l.node.updateCommitIndex(commitIndex)
			}
		}

		l.sendAppend(peer)
	}()
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
	// ignore
}

func (c *Candidate) electAsLeader() {
	n := c.node
	c.node.logger.Printf("%s start election", n.Id)

	meta := n.meta.GetMeta()
	meta.VoteFor = c.node.Id
	meta.CurrentTerm += 1
	if err := n.meta.SaveMeta(meta.CurrentTerm, meta.VoteFor); err != nil {
		c.node.logger.Printf("%s elect as leader failed due to error: %s", n.Id, err)
		timeout := calculateElectionTimeout(n.raftConfigs)
		c.cancelElectionTimeoutFunc = n.scheduleOnce(timeout, c.electAsLeader)
		return
	}

	req := c.buildRequestVoteRequest()
	resultCh := c.broadcastRequestVote(req)
	go func() {
		reps := <-resultCh
		n.lock.Lock()
		defer n.lock.Unlock()
		if n.state != c {
			return
		}
		meta := n.meta.GetMeta()
		if meta.CurrentTerm != req.Term {
			return
		}

		ballot := NewBallot(n.Id, n.peers, n.Id)
		ballot.Count(n.Id)
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
			timeout := calculateElectionTimeout(n.raftConfigs)
			c.cancelElectionTimeoutFunc = n.scheduleOnce(timeout, c.electAsLeader)
		}
	}()
}

func (c *Candidate) buildRequestVoteRequest() *protos.RequestVoteRequest {
	n := c.node

	meta := n.meta.GetMeta()
	lastLog := n.logStorage.LastEntry()
	return &protos.RequestVoteRequest{
		Term:         int64(meta.CurrentTerm),
		CandidateId:  n.Id,
		LastLogIndex: lastLog.Index,
		LastLogTerm:  lastLog.Term,
	}
}

func (c *Candidate) broadcastRequestVote(req *protos.RequestVoteRequest) chan map[string]*protos.RequestVoteResponse {
	responseCh := make(chan map[string]*protos.RequestVoteResponse)
	go func() {
		defer close(responseCh)
		type PeerAndResponse struct {
			peer    *PeerNode
			channel chan *RpcResponse[*protos.RequestVoteResponse]
		}

		peerResponses := []*PeerAndResponse{}
		for _, peer := range c.node.peers {
			_, ch := c.node.rpcClient.RequestVote(c.node.Id, peer.Endpoint, req)
			peerResponses = append(peerResponses, &PeerAndResponse{peer, ch})
		}

		responses := make(map[string]*protos.RequestVoteResponse)
		for _, pr := range peerResponses {
			r := <-pr.channel
			if r.err != nil {
				c.node.logger.Printf("request vote for peer: %s failed. %s", pr.peer.Id, r.err)
				continue
			}
			responses[pr.peer.Id] = r.response
		}
		responseCh <- responses
	}()

	return responseCh
}

type PeerNode struct {
	Id              string
	Endpoint        *protos.Endpoint
	flyingAppendMid string
	nextIndex       int64
	matchIndex      int64
}

func NewPeerNodes(es []*protos.Endpoint) map[string]*PeerNode {
	peers := make(map[string]*PeerNode)
	for _, e := range es {
		id := e.NodeId
		peer := PeerNode{Id: id, Endpoint: e, flyingAppendMid: ""}
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
		n.logger.Printf("update applied index to: %d", index)
	}
	n.applyLogEntriesToStateMachine()
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
	n.transferState(&Leader{node: n, ballots: NewAppendEntriesBollots(n)})
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
		n.applyLogEntriesToStateMachine()
	} else {
		n.logger.Printf("skip update commit index to: %d, currentCommitIndex: %d", commitIndex, n.commitIndex)
	}
}

func (n *Node) applyLogEntriesToStateMachine() {
	if n.lastApplied >= n.commitIndex {
		return
	}

	entries, err := n.logStorage.GetEntriesInRange(n.lastApplied, n.commitIndex+1)
	if err != nil {
		n.logger.Printf("gen entries in range:[%d, %d) failed", n.lastApplied, n.commitIndex+1)
		return
	}
	n.ApplyLogChan <- entries
}

func calculateElectionTimeout(config RaftConfigurations) int64 {
	for {
		timeout := rand.Int63n(config.ElectionTimeoutMs)
		if timeout > config.PingTimeoutMs {
			return timeout
		}
	}
}
