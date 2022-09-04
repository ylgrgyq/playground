package consensus

import (
	"fmt"
	"gopkg.in/yaml.v2"
	"io/ioutil"
	"regexp"
	"strings"
	"ylgrgyq.com/go-consensus/consensus/protos"
)

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

type RaftConfigurations struct {
	PingTimeoutMs     int64
	ElectionTimeoutMs int64
}

type Configurations struct {
	RpcType            RpcType
	SelfEndpoint       protos.Endpoint
	PeerEndpoints      []protos.Endpoint
	RaftConfigurations RaftConfigurations
}

func (c *Configurations) String() string {
	b := strings.Builder{}
	b.WriteString("\n")
	b.WriteString("********************************* Configurations *********************************\n\n")
	b.WriteString(fmt.Sprintf("NodeId: %s\n", c.SelfEndpoint.NodeId))
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
	ElectionTimeoutMs int64 `yaml:"electionTimeoutMs"`
	PingTimeoutMs     int64 `yaml:"pingTimeoutMs"`
}

func (yc *yamlRaftConfigurations) toRaftConfigurations() RaftConfigurations {
	return RaftConfigurations{
		ElectionTimeoutMs: yc.ElectionTimeoutMs,
		PingTimeoutMs:     yc.PingTimeoutMs,
	}
}

type yamlConfigurations struct {
	RpcType            string                 `yaml:"rpcType"`
	SelfEndpoint       yamlEndpoint           `yaml:"selfEndpoint"`
	PeerEndpoints      []yamlEndpoint         `yaml:"peerEndpoints"`
	RaftConfigurations yamlRaftConfigurations `yaml:"raftConfigurations"`
}

func (ye *yamlEndpoint) toStdEndpoint() (*protos.Endpoint, error) {
	if len(ye.NodeId) == 0 {
		return nil, fmt.Errorf("invalid empty NodeId")
	}
	if !regexp.MustCompile(`^[a-zA-Z0-9]*$`).MatchString(ye.NodeId) {
		return nil, fmt.Errorf("nodeId must be an alphanumeric string")
	}

	if len(ye.IP) == 0 {
		return nil, fmt.Errorf("invalid empty IP")
	}
	if ye.Port <= 0 {
		return nil, fmt.Errorf("invalid port: %d", ye.Port)
	}
	return &protos.Endpoint{
		NodeId: ye.NodeId,
		Ip:   ye.IP,
		Port: ye.Port,
	}, nil
}

func (yc *yamlConfigurations) toStdConfigurations() (*Configurations, error) {
	if yc.RpcType == UnknownRpcType {
		return nil, fmt.Errorf("unknown rpc type: %s", yc.RpcType)
	}
	selfEndpoint, err := yc.SelfEndpoint.toStdEndpoint()
	if err != nil {
		return nil, fmt.Errorf("SelfEndpoint, %s", err.Error())
	}
	peers := make([]protos.Endpoint, 0)
	for _, peer := range yc.PeerEndpoints {
		peerEndpoint, err := peer.toStdEndpoint()
		if err != nil {
			return nil, fmt.Errorf("PeerEndpoint, %s", err.Error())
		}

		if selfEndpoint.NodeId == peerEndpoint.NodeId {
			return nil, fmt.Errorf("SelfEndpoint can't in peer endpoints")
		}

		peers = append(peers, *peerEndpoint)
	}

	return &Configurations{
		SelfEndpoint:       *selfEndpoint,
		RpcType:            toValidRpcType(yc.RpcType),
		PeerEndpoints:      peers,
		RaftConfigurations: yc.RaftConfigurations.toRaftConfigurations(),
	}, nil
}

func ParseConfig(configFilePath string) (*Configurations, error) {
	bs, err := ioutil.ReadFile(configFilePath)
	if err != nil {
		return nil, fmt.Errorf("read configurations from file %s failed. caused by: %s", configFilePath, err)
	}

	var yConfig yamlConfigurations
	err = yaml.Unmarshal(bs, &yConfig)
	if err != nil {
		return nil, fmt.Errorf("parse configurations in yaml failed. cause by: %s", err)
	}
	return yConfig.toStdConfigurations()
}
