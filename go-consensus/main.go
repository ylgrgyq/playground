package main

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

type Meta struct {
	currentTerm int64
	votedFor    NodeEndpoint
}

type Configurations struct {
}

type Node struct {
	nodeEndpoint NodeEndpoint
	state        State
	meta         Meta
	commitIndex  int64
	lastApplied  int64
}

func newNode(endpoint NodeEndpoint) *Node {
	return &Node{
		nodeEndpoint: endpoint,
		state:        &Follower{},
		commitIndex:  0,
		lastApplied:  0,
	}
}

func main() {
	serverLogger.Ok("Ok")
}