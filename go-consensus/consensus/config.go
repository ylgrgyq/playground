package consensus

import (
	"fmt"
	"gopkg.in/yaml.v2"
	"io/ioutil"
	"strings"
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
	ElectionTimeoutMs int64
}

type Configurations struct {
	RpcType            RpcType
	SelfEndpoint       Endpoint
	PeerEndpoints      []Endpoint
	RaftConfigurations RaftConfigurations
}

func (c *Configurations) String() string {
	b := strings.Builder{}
	b.WriteString("\n")
	b.WriteString("********************************* Configurations *********************************\n\n")
	b.WriteString(fmt.Sprintf("RpcType: %s\n", c.RpcType))
	b.WriteString(fmt.Sprintf("IP: %s\n", c.SelfEndpoint.IP))
	b.WriteString(fmt.Sprintf("Port: %d\n", c.SelfEndpoint.Port))
	b.WriteString("\n")
	b.WriteString("PeerEndpoints:\n")
	for _, peer := range c.PeerEndpoints {
		b.WriteString(fmt.Sprintf("  IP: %s\n", peer.IP))
		b.WriteString(fmt.Sprintf("  Port: %d\n", peer.Port))
		b.WriteString("\n")
	}
	b.WriteString("RaftConfigurations:\n")
	b.WriteString(fmt.Sprintf("  ElectionTimeout: %dms\n", c.RaftConfigurations.ElectionTimeoutMs))
	b.WriteString("\n")
	b.WriteString("**********************************************************************************\n")
	return b.String()
}

type yamlEndpoint struct {
	IP   string `yaml:"ip"`
	Port uint16 `yaml:"port"`
}

type yamlRaftConfigurations struct {
	ElectionTimeoutMs int64 `yaml:"electionTimeoutMs"`
}

func (yc *yamlRaftConfigurations) toRaftConfigurations() RaftConfigurations {
	return RaftConfigurations{ElectionTimeoutMs: yc.ElectionTimeoutMs}
}

type yamlConfigurations struct {
	RpcType            string                 `yaml:"rpcType"`
	SelfEndpoint       yamlEndpoint           `yaml:"selfEndpoint"`
	PeerEndpoints      []yamlEndpoint         `yaml:"peerEndpoints"`
	RaftConfigurations yamlRaftConfigurations `yaml:"raftConfigurations"`
}

func (ye *yamlEndpoint) toStdEndpoint() Endpoint {
	return Endpoint{
		IP:   ye.IP,
		Port: ye.Port,
	}
}

func (yc *yamlConfigurations) toStdConfigurations() (*Configurations, error) {
	if yc.RpcType == UnknownRpcType {
		return nil, fmt.Errorf("unknown rpc type: %s", yc.RpcType)
	}
	peers := make([]Endpoint, 0)
	for _, peer := range yc.PeerEndpoints {
		peers = append(peers, peer.toStdEndpoint())
	}
	return &Configurations{
		SelfEndpoint:       yc.SelfEndpoint.toStdEndpoint(),
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
