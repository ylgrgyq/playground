package consensus

type LogEntry struct {
	Index int64
	Term  int64
	data  []byte
}

type LogStorage interface {
	LastEntry() LogEntry
}
