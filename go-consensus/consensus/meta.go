package consensus

import (
	"context"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type Meta struct {
	CurrentTerm Term
	VoteFor     *protos.Endpoint
}

type MetaStorage interface {
	Start() error
	Shutdown(ctx context.Context) error
	SaveMeta(meta Meta) error
	GetMeta() Meta
}

type TestingMeta struct {
	meta Meta
}

func (t *TestingMeta) Start() error {
	serverLogger.Debugf("start with meta %+v", t.meta)
	return nil
}

func (t *TestingMeta) Shutdown(ctx context.Context) error {
	return nil
}

func (t *TestingMeta) SaveMeta(meta Meta) error {
	serverLogger.Debugf("save new meta %+v", meta)
	t.meta = meta
	return nil
}

func (t *TestingMeta) GetMeta() Meta {
	return t.meta
}

func NewTestingMeta() MetaStorage {
	meta := Meta{1, nil}
	return &TestingMeta{meta: meta}
}


