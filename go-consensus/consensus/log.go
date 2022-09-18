package consensus

import (
	"context"
	"log"

	"ylgrgyq.com/go-consensus/consensus/protos"
)

type LogStorage interface {
	Start() error
	Append(entry *protos.LogEntry) error
	AppendEntries(prevLogTerm int64, prevLogIndex int64, entries []*protos.LogEntry) (bool, error)
	IsAtLeastUpToDateThanMe(term int64, index int64) bool
	LastEntry() *protos.LogEntry
	GetEntry(index int64) (*protos.LogEntry, bool)
	GetEntriesFromIndex(fromIndex int64) ([]*protos.LogEntry, error)
	GetEntriesInRange(fromIndex int64, toIndex int64) ([]*protos.LogEntry, error)
	Stop(ctx context.Context)
}

type MemoryLogStorage struct {
	entries []*protos.LogEntry
}

func NewLogStorage() LogStorage {
	return &MemoryLogStorage{}
}

func (l *MemoryLogStorage) IsAtLeastUpToDateThanMe(term int64, index int64) bool {
	lastEntry := l.LastEntry()

	if term > lastEntry.Term {
		return true
	}

	if lastEntry.Term == term && index >= lastEntry.Index {
		return true
	}

	return false
}

func (l *MemoryLogStorage) Start() error {
	initEntry := protos.LogEntry{Index: 0, Term: 0, Data: []byte{}}
	l.entries = []*protos.LogEntry{&initEntry}
	return nil
}

func (l *MemoryLogStorage) Stop(ctx context.Context) {
}

func (l *MemoryLogStorage) Append(entry *protos.LogEntry) error {
	if len(l.entries) == 0 {
		log.Fatal("storage not initialized")
	}
	l.entries = append(l.entries, entry)
	return nil
}

func (l *MemoryLogStorage) AppendEntries(prevLogTerm int64, prevLogIndex int64, entries []*protos.LogEntry) (bool, error) {
	if len(l.entries) == 0 {
		log.Fatal("storage not initialized")
	}
	i, ok := l.getArrayIndex(prevLogIndex)
	if !ok {
		return false, nil
	}
	prevEntry := l.entries[i]
	if prevEntry.Term != prevLogTerm {
		return false, nil
	}

	l.entries = append(l.entries[:i+1], entries...)
	return true, nil
}

func (l *MemoryLogStorage) LastEntry() *protos.LogEntry {
	if len(l.entries) == 0 {
		log.Fatal("storage not initialized")
	}

	return l.entries[len(l.entries)-1]
}

func (l *MemoryLogStorage) GetEntry(index int64) (*protos.LogEntry, bool) {
	if len(l.entries) == 0 {
		return nil, false
	}

	i, ok := l.getArrayIndex(index)
	if !ok {
		return nil, false
	}

	return l.entries[i], true
}

func (l *MemoryLogStorage) GetEntriesFromIndex(fromIndex int64) ([]*protos.LogEntry, error) {
	if len(l.entries) == 0 {
		log.Fatal("storage not initialized")
	}
	firstIndex := l.entries[0]
	i := Max(0, int(fromIndex-firstIndex.Index))
	return l.entries[i:], nil
}

func (l *MemoryLogStorage) GetEntriesInRange(fromIndex int64, toIndex int64) ([]*protos.LogEntry, error) {
	if len(l.entries) == 0 {
		log.Fatal("storage not initialized")
	}
	firstIndex := l.entries[0]
	start := Max(0, int(fromIndex-firstIndex.Index))
	end := Min(int(toIndex-firstIndex.Index), len(l.entries))

	if start >= end {
		return []*protos.LogEntry{}, nil
	}
	return l.entries[start:end], nil
}

func (l *MemoryLogStorage) getArrayIndex(index int64) (int, bool) {
	firstIndex := l.entries[0]
	i := int(index - firstIndex.Index)
	if i < 0 || i >= len(l.entries) {
		return i, false
	}
	return i, true
}
