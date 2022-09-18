package consensus

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"

	"google.golang.org/protobuf/proto"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

type Meta protos.Meta

type MetaStorage interface {
	LoadMeta() error
	Stop(ctx context.Context)
	SaveMeta(currentTerm int64, voteFor string) error
	GetMeta() *Meta
}

func NewMeta(configs *Configurations, logger *log.Logger) MetaStorage {
	meta := Meta{CurrentTerm: 1, VoteFor: ""}
	metaLogger := log.New(logger.Writer(), fmt.Sprintf("[Meta-%s]", configs.SelfEndpoint.NodeId), logger.Flags())
	return &FileMetaStorage{meta: &meta, logger: metaLogger, metaDir: configs.MetaStorageDirectory, nodeId: configs.SelfEndpoint.NodeId}
}

type FileMetaStorage struct {
	nodeId  string
	meta    *Meta
	logger  *log.Logger
	metaDir string
}

func (t *FileMetaStorage) LoadMeta() error {
	err := os.MkdirAll(t.metaDir, 0700)
	if err != nil {
		return fmt.Errorf("create meta storage directory failed. error: %s", err)
	}

	metaFilePath := filepath.Join(t.metaDir, t.nodeId)
	f, err := os.OpenFile(metaFilePath, os.O_RDONLY, 0444)
	if err == nil {
		defer func() {
			_ = f.Close()
		}()
		if meta, err := t.loadMetaFromFile(f); err != nil {
			return err
		} else {
			t.meta = meta
		}
	} else {
		if !os.IsNotExist(err) {
			return fmt.Errorf("cannot open file: %s", err)
		}
	}

	t.logger.Printf("start with meta. CurrentTerm: %d, VoteFor: %s", t.meta.CurrentTerm, t.meta.VoteFor)
	return nil
}

func (t *FileMetaStorage) Stop(ctx context.Context) {
}

func (t *FileMetaStorage) SaveMeta(currentTerm int64, voteFor string) error {
	t.logger.Printf("save new meta. CurrentTerm: %d, VoteFor: %s", currentTerm, voteFor)

	meta := protos.Meta{CurrentTerm: currentTerm, VoteFor: voteFor}
	metaBytes, err := proto.Marshal(&meta)
	if err != nil {
		return fmt.Errorf("marshal meta failed. error: %s", err)
	}

	tempFile, err := os.CreateTemp(t.metaDir, t.nodeId)
	if err != nil {
		return fmt.Errorf("create meta temp file failed. error: %s", err)
	}

	defer func() {
		_ = os.Remove(tempFile.Name())
	}()

	defer func() {
		_ = tempFile.Close()
	}()

	tempFileName := tempFile.Name()
	if _, err = tempFile.Write(metaBytes); err != nil {
		return fmt.Errorf("write meta to temp file failed. tempfile: %q, error: %s", tempFileName, err)
	}

	if err = tempFile.Sync(); err != nil {
		return fmt.Errorf("flush meta to temp file failed. tempfile %q, error: %s", tempFileName, err)
	}

	if err = tempFile.Close(); err != nil {
		return fmt.Errorf("close temp meta file failed. tempfile %q, error: %s", tempFileName, err)
	}

	if err = os.Chmod(tempFileName, 0444); err != nil {
		return fmt.Errorf("chmod temp meta file failed. tempfile %q, error: %s", tempFileName, err)
	}

	err = os.Rename(tempFile.Name(), filepath.Join(t.metaDir, t.nodeId))
	if err != nil {
		return fmt.Errorf("replace meta file failed. error: %s", err)
	}

	m := Meta(meta)
	t.meta = &m

	return nil
}

func (t *FileMetaStorage) GetMeta() *Meta {
	return t.meta
}

func (t *FileMetaStorage) metaFilePath() string {
	return filepath.Join(t.metaDir, t.nodeId)
}

func (t *FileMetaStorage) loadMetaFromFile(f *os.File) (*Meta, error) {
	metaFilePath := t.metaFilePath()
	metaData, err := os.ReadFile(metaFilePath)
	if err != nil && !os.IsNotExist(err) {
		return nil, fmt.Errorf("cannot open file: %s", err)
	}

	if len(metaData) == 0 {
		return nil, fmt.Errorf("meta file is empty. file: %s", metaFilePath)
	}

	var meta protos.Meta
	if err := proto.Unmarshal(metaData, &meta); err != nil {
		return nil, fmt.Errorf("unmarshal meta file failed. file: %s. error: %s", metaFilePath, err)
	}
	m := Meta(meta)
	return &m, nil
}
