package consensus

import (
	"fmt"
	"gopkg.in/yaml.v2"
	"io/ioutil"
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
	b.WriteString(fmt.Sprintf("RpcType: %s\n", c.RpcType))
	b.WriteString(fmt.Sprintf("IP: %s\n", c.SelfEndpoint.Ip))
	b.WriteString(fmt.Sprintf("Port: %d\n", c.SelfEndpoint.Port))
	b.WriteString("\n")
	b.WriteString("PeerEndpoints:\n")
	for _, peer := range c.PeerEndpoints {
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
	IP   string `yaml:"ip"`
	Port uint32 `yaml:"port"`
}

type yamlRaftConfigurations struct {
	ElectionTimeoutMs int64 `yaml:"electionTimeoutMs"`
	PingTimeoutMs     int64 `yaml:"pingTimeoutMs"`
}

func (yc *yamlRaftConfigurations) toRaftConfigurations() RaftConfigurations {
	return RaftConfigurations{
		ElectionTimeoutMs: yc.ElectionTimeoutMs,
		PingTimeoutMs: yc.PingTimeoutMs,
	}
}

type yamlConfigurations struct {
	RpcType            string                 `yaml:"rpcType"`
	SelfEndpoint       yamlEndpoint           `yaml:"selfEndpoint"`
	PeerEndpoints      []yamlEndpoint         `yaml:"peerEndpoints"`
	RaftConfigurations yamlRaftConfigurations `yaml:"raftConfigurations"`
}

func (ye *yamlEndpoint) toStdEndpoint() protos.Endpoint {
	return protos.Endpoint{
		Ip:   ye.IP,
		Port: ye.Port,
	}
}

func (yc *yamlConfigurations) toStdConfigurations() (*Configurations, error) {
	if yc.RpcType == UnknownRpcType {
		return nil, fmt.Errorf("unknown rpc type: %s", yc.RpcType)
	}
	selfEndpoint := yc.SelfEndpoint.toStdEndpoint()
	peers := make([]protos.Endpoint, 0)
	for _, peer := range yc.PeerEndpoints {
		peerEndpoint := peer.toStdEndpoint()
		if selfEndpoint.Ip == peerEndpoint.Ip && selfEndpoint.Port == peerEndpoint.Port {
			return nil, fmt.Errorf("self endpoint can't in peer endpoints")
		}

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
