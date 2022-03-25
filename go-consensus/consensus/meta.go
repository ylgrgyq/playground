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
	GetMeta() (Meta, error)
}

type TestingMeta struct {
	Meta Meta
}

func (t *TestingMeta) Start() error {
	return nil
}

func (t *TestingMeta) Shutdown(ctx context.Context) error {
	return nil
}

func (t *TestingMeta) SaveMeta(meta Meta) error {
	t.Meta = meta
}

func (t *TestingMeta) GetMeta() (Meta, error) {
	return t.Meta, nil
}

func NewTestingMeta() MetaStorage {
	meta := Meta{1, nil}
	return &TestingMeta{Meta: meta}
}


