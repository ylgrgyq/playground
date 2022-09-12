package consensus

import "ylgrgyq.com/go-consensus/consensus/protos"

type LogStorage interface {
	IsAtLeastUpToDateThanMe(term int64, index int64) bool
	LastEntry() *protos.LogEntry
	GetEntry(index int64) *protos.LogEntry
	GetEntries(fromIndex int64) []*protos.LogEntry
}

type TestingLogStorage struct {
}

func (l *TestingLogStorage) IsAtLeastUpToDateThanMe(term int64, index int64) bool {
	lastEntry := l.LastEntry();
	if term > lastEntry.Term  {
		return true
	}

	if lastEntry.Term == term && index >= lastEntry.Index {
		return true
	}

	return false
}


func (l *TestingLogStorage) LastEntry() *protos.LogEntry {
	return &protos.LogEntry{}
}

func (l *TestingLogStorage) GetEntry(index int64) *protos.LogEntry {
	return &protos.LogEntry{}
}

func (l *TestingLogStorage) GetEntries(fromIndex int64) []*protos.LogEntry {
	return make([]*protos.LogEntry, 4)
}
