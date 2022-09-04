package consensus

import (
	"context"
	"fmt"
	"log"
)

type Meta struct {
	CurrentTerm int64
	VoteFor     string
}

type MetaStorage interface {
	LoadMeta() error
	Shutdown(ctx context.Context) error
	SaveMeta(meta Meta) error
	GetMeta() Meta
}

type TestingMeta struct {
	meta   Meta
	logger *log.Logger
}

func (t *TestingMeta) LoadMeta() error {
	t.logger.Printf("start with meta %+v", t.meta)
	return nil
}

func (t *TestingMeta) Shutdown(ctx context.Context) error {
	return nil
}

func (t *TestingMeta) SaveMeta(meta Meta) error {
	t.logger.Printf("save new meta %+v", meta)
	t.meta = meta
	return nil
}

func (t *TestingMeta) GetMeta() Meta {
	return t.meta
}

func NewTestingMeta(nodeId string, logger *log.Logger) MetaStorage {
	meta := Meta{1, ""}
	return &TestingMeta{meta: meta, logger: log.New(logger.Writer(), fmt.Sprintf("[Meta-%s]", nodeId), logger.Flags())}
}
