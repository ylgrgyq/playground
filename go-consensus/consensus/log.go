package consensus

import (
	"context"
	"fmt"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type LogStorage interface {
	Start() error
	Append(entry *protos.LogEntry) error
	AppendEntries(prevLogTerm int64, prevLogIndex int64, entries []*protos.LogEntry) (bool, error)
	IsAtLeastUpToDateThanMe(term int64, index int64) bool
	LastEntry() *protos.LogEntry
	GetEntry(index int64) (*protos.LogEntry, error)
	GetEntries(fromIndex int64) ([]*protos.LogEntry, error)
	Stop(ctx context.Context)
}

type TestingLogStorage struct {
	entries []*protos.LogEntry
}

func NewLogStorage() LogStorage {
	return &TestingLogStorage{}
}

func (l *TestingLogStorage) IsAtLeastUpToDateThanMe(term int64, index int64) bool {
	lastEntry := l.LastEntry()

	if term > lastEntry.Term {
		return true
	}

	if lastEntry.Term == term && index >= lastEntry.Index {
		return true
	}

	return false
}

func (l *TestingLogStorage) Start() error {
	initEntry := protos.LogEntry{Index: 0, Term: 0, Data: []byte{}}
	l.entries = []*protos.LogEntry{&initEntry}
	return nil
}

func (l *TestingLogStorage) Stop(ctx context.Context) {
}

func (l *TestingLogStorage) Append(entry *protos.LogEntry) error {
	l.entries = append(l.entries, entry)
	return nil
}

func (l *TestingLogStorage) AppendEntries(prevLogTerm int64, prevLogIndex int64, entries []*protos.LogEntry) (bool, error) {
	index := -1
	for i := len(l.entries) - 1; i >= 0; i-- {
		if l.entries[i].Index == prevLogIndex {
			index = i
			break
		}
	}
	if index >= 0 {
		prevEntry := l.entries[index]
		if prevEntry.Term != prevLogTerm {
			return false, nil
		}

		l.entries = append(l.entries[:index + 1], entries...)
		return true, nil
	}
	return false, nil
}

func (l *TestingLogStorage) LastEntry() *protos.LogEntry {
	return l.entries[len(l.entries)-1]
}

func (l *TestingLogStorage) GetEntry(index int64) (*protos.LogEntry, error) {
	for _, entry := range l.entries {
		if entry.Index == index {
			return entry, nil
		}
	}
	return nil, fmt.Errorf("log entry with index: %d is not found", index)
}

func (l *TestingLogStorage) GetEntries(fromIndex int64) ([]*protos.LogEntry, error) {
	index := -1
	for i := len(l.entries) - 1; i >= 0; i-- {
		if l.entries[i].Index == fromIndex {
			index = i
			break
		}
	}
	if index >= 0 {
		return l.entries[index:], nil
	}
	return make([]*protos.LogEntry, 0), nil
}
