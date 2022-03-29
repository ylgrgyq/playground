package consensus

type LogEntry struct {
	Index int64
	Term  int64
	data  []byte
}

func (l *LogEntry) IsAtLeastUpToDateThanMe(term int64, index int64) bool {
	if term > l.Term  {
		return true
	}

	if l.Term == term && index >= l.Index {
		return true
	}

	return false
}

type LogStorage interface {
	LastEntry() LogEntry
}

type TestingLogStorage struct {
}

func (l *TestingLogStorage) LastEntry() LogEntry {
	return LogEntry{}
}
