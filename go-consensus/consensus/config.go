package consensus

import (
	"fmt"
	"io/ioutil"
	"strings"

	"gopkg.in/yaml.v2"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

const PROGRAM_NAME = "go-consensus"

type RpcType string

const (
	UnknownRpcType = "unknown"
	HttpRpcType    = "http"
)

var RpcTypes = []RpcType{
	HttpRpcType,
}

func toValidRpcType(rpcTypeStr string) RpcType {
	lowerCaseRpcType := strings.ToLower(rpcTypeStr)
	for _, rpcType := range RpcTypes {
		if rpcType == RpcType(lowerCaseRpcType) {
			return rpcType
		}
	}
	return UnknownRpcType
}

func (r *RpcType) Validate() error {
	if *r == UnknownRpcType {
		return fmt.Errorf("unknown rpc type")
	}
	return nil
}

type Config interface {
	Print()
	Validate() error
}

type ConfigurationsParser interface {
	Parse(bs []byte) Configurations
}

type RaftConfigurations struct {
	MetaStorageDirectory string
	PingTimeoutMs        int64
	ElectionTimeoutMs    int64
}

type HttpRpcConfigurations struct {
	RpcTimeoutMs int64
}

type Configurations struct {
	RpcType               RpcType
	RpcTimeoutMs          int64
	SelfEndpoint          *protos.Endpoint
	PeerEndpoints         []*protos.Endpoint
	RaftConfigurations    RaftConfigurations
	HttpRpcConfigurations HttpRpcConfigurations
}

func NewRaftConfigurations() *RaftConfigurations {
	return &RaftConfigurations{
		MetaStorageDirectory: "/tmp/" + PROGRAM_NAME,
	}
}

func (r *RaftConfigurations) Validate() error {
	if r.PingTimeoutMs <= 0 {
		return fmt.Errorf("invalid PingTimeoutMs: %d", r.PingTimeoutMs)
	}
	if r.ElectionTimeoutMs <= 0 {
		return fmt.Errorf("invalid ElectionTimeoutMs: %d", r.ElectionTimeoutMs)
	}

	if r.ElectionTimeoutMs < 2*r.PingTimeoutMs {
		return fmt.Errorf("invalid ElectionTimeoutMs: %d. ElectionTimeoutMs is at least twice as large as PingTimeoutMs", r.ElectionTimeoutMs)
	}

	if len(r.MetaStorageDirectory) <= 0 {
		return fmt.Errorf("empty MetaStorageDirectory")
	}

	return nil
}

// func (ye *yamlEndpoint) toStdEndpoint() (*protos.Endpoint, error) {
// 	if len(ye.NodeId) == 0 {
// 		return nil, fmt.Errorf("invalid empty NodeId")
// 	}
// 	if !regexp.MustCompile(`^[a-zA-Z0-9]*$`).MatchString(ye.NodeId) {
// 		return nil, fmt.Errorf("nodeId must be an alphanumeric string")
// 	}

// 	if len(ye.IP) == 0 {
// 		return nil, fmt.Errorf("invalid empty IP")
// 	}
// 	if ye.Port <= 0 {
// 		return nil, fmt.Errorf("invalid port: %d", ye.Port)
// 	}
// 	return &protos.Endpoint{
// 		NodeId: ye.NodeId,
// 		Ip:     ye.IP,
// 		Port:   ye.Port,
// 	}, nil
// }

func (c *Configurations) Validate() error {
	if err := c.RpcType.Validate(); err != nil {
		return err
	}

	// selfEndpoint, err := yc.SelfEndpoint.toStdEndpoint()
	// if err != nil {
	// 	return nil, fmt.Errorf("SelfEndpoint, %s", err.Error())
	// }
	// peers := make([]*protos.Endpoint, 0)
	// for _, peer := range yc.PeerEndpoints {
	// 	peerEndpoint, err := peer.toStdEndpoint()
	// 	if err != nil {
	// 		return nil, fmt.Errorf("PeerEndpoint, %s", err.Error())
	// 	}

	// 	if selfEndpoint.NodeId == peerEndpoint.NodeId {
	// 		return nil, fmt.Errorf("SelfEndpoint can't in peer endpoints")
	// 	}

	// 	peers = append(peers, peerEndpoint)
	// }

	return nil
}

func (c *Configurations) String() string {
	b := strings.Builder{}
	b.WriteString("\n")
	b.WriteString("********************************* Configurations *********************************\n\n")
	b.WriteString(fmt.Sprintf("NodeId: %s\n", c.SelfEndpoint.NodeId))
	b.WriteString("\n")
	// b.WriteString(fmt.Sprintf("MetaStorageDir: %s\n", c.MetaStorageDirectory))
	b.WriteString("\n")
	b.WriteString(fmt.Sprintf("RpcType: %s\n", c.RpcType))
	b.WriteString(fmt.Sprintf("IP: %s\n", c.SelfEndpoint.Ip))
	b.WriteString(fmt.Sprintf("Port: %d\n", c.SelfEndpoint.Port))
	b.WriteString("\n")
	b.WriteString("PeerEndpoints:\n")
	for _, peer := range c.PeerEndpoints {
		b.WriteString(fmt.Sprintf("  NodeId: %s\n", peer.NodeId))
		b.WriteString(fmt.Sprintf("  IP: %s\n", peer.Ip))
		b.WriteString(fmt.Sprintf("  Port: %d\n", peer.Port))
		b.WriteString("\n")
	}
	b.WriteString("RaftConfigurations:\n")
	b.WriteString(fmt.Sprintf("  PingTimeoutMs: %dms\n", c.RaftConfigurations.PingTimeoutMs))
	b.WriteString(fmt.Sprintf("  ElectionTimeout: %dms\n", c.RaftConfigurations.ElectionTimeoutMs))
	b.WriteString("\n")
	b.WriteString("**********************************************************************************\n")
	return b.String()
}

type yamlEndpoint struct {
	NodeId string `yaml:"nodeId"`
	IP     string `yaml:"ip"`
	Port   uint32 `yaml:"port"`
}

type yamlRaftConfigurations struct {
	MetaStorageDirectory string `yaml:"metaStorageDirectory"`
	ElectionTimeoutMs    int64  `yaml:"electionTimeoutMs"`
	PingTimeoutMs        int64  `yaml:"pingTimeoutMs"`
}

type yamlHttpRpcConfigurations struct {
	RpcTimeoutMs int64 `yaml:"rpcTimeoutMs"`
}

type yamlConfigurations struct {
	RpcType               string                    `yaml:"rpcType"`
	SelfEndpoint          yamlEndpoint              `yaml:"selfEndpoint"`
	PeerEndpoints         []yamlEndpoint            `yaml:"peerEndpoints"`
	RaftConfigurations    yamlRaftConfigurations    `yaml:"raftConfigurations"`
	HttpRpcConfigurations yamlHttpRpcConfigurations `yaml:"httpRpcConfigurations"`
}

func (yc yamlConfigurations) ParseConfig(bs []byte) (*Configurations, error) {
	err := yaml.Unmarshal(bs, yc)
	if err != nil {
		return nil, fmt.Errorf("parse configurations in yaml failed. cause by: %s", err)
	}
	config, err := yc.toConfigurations()
	if err != nil {
		return nil, err
	}

	return config, nil
}

func (yc *yamlRaftConfigurations) toRaftConfigurations() *RaftConfigurations {
	c := NewRaftConfigurations()
	c.PingTimeoutMs = yc.PingTimeoutMs
	c.ElectionTimeoutMs = yc.ElectionTimeoutMs
	c.MetaStorageDirectory = yc.MetaStorageDirectory
	return c
}

func (ye *yamlEndpoint) toStdEndpoint() *protos.Endpoint {
	return &protos.Endpoint{
		NodeId: ye.NodeId,
		Ip:     ye.IP,
		Port:   ye.Port,
	}
}

func (y *yamlHttpRpcConfigurations) toHttpConfigurations() *HttpRpcConfigurations {
	return &HttpRpcConfigurations{
		RpcTimeoutMs: y.RpcTimeoutMs,
	}
}

func (yc *yamlConfigurations) toConfigurations() (*Configurations, error) {
	selfEndpoint := yc.SelfEndpoint.toStdEndpoint()
	peers := make([]*protos.Endpoint, 0)
	for _, peer := range yc.PeerEndpoints {
		peerEndpoint := peer.toStdEndpoint()
		peers = append(peers, peerEndpoint)
	}

	return &Configurations{
		SelfEndpoint:          selfEndpoint,
		RpcType:               toValidRpcType(yc.RpcType),
		PeerEndpoints:         peers,
		RaftConfigurations:    *yc.RaftConfigurations.toRaftConfigurations(),
		HttpRpcConfigurations: *yc.HttpRpcConfigurations.toHttpConfigurations(),
	}, nil
}

func ParseConfig(configFilePath string) (*Configurations, error) {
	bs, err := ioutil.ReadFile(configFilePath)
	if err != nil {
		return nil, fmt.Errorf("read configurations from file %s failed. caused by: %s", configFilePath, err)
	}

	c, err := yamlConfigurations{}.ParseConfig(bs)
	if err != nil {
		return nil, err
	}

	if err = c.Validate(); err != nil {
		return nil, err
	}
	return c, nil
}
