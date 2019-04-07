package code

import (
	"encoding/binary"
	"fmt"
)

type Instructions []byte
type OpCode byte

const (
	OpConstant OpCode = iota
)

type Definition struct {
	Name         string
	OperandWiths []int
}

var definitionMap = map[OpCode]*Definition{
	OpConstant: &Definition{"OpConstant", []int{2}},
}

func Lookup(code OpCode) (*Definition, error) {
	def, ok := definitionMap[code]
	if !ok {
		return nil, fmt.Errorf("can not find definition for code %d", code)
	}
	return def, nil
}

func Make(op OpCode, operands ...int) []byte {
	def, ok := definitionMap[op]
	if !ok {
		return []byte{}
	}

	length := 0
	for _, width := range def.OperandWiths {
		length += width
	}

	instructions := make([]byte, length)
	instructions[0] = byte(op)

	offset := 1
	for i, operand := range operands {
		expectWidth := def.OperandWiths[i]
		switch expectWidth {
		case 2:
			binary.BigEndian.PutUint16(instructions[offset:], uint16(operand))
		}
		offset += expectWidth
	}

	return instructions
}
