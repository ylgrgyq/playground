package code

import "testing"

func TestMake(t *testing.T) {
	tests := []struct {
		code                 OpCode
		operands             []int
		expectedInstructions []byte
	}{
		{OpConstant, []int{65534}, []byte{}},
	}

	for _, test := range tests {
		instructions := Make(test.code, test.operands...)

		if len(instructions) != len(test.expectedInstructions) {
			t.Errorf("instructions length not equal. want=%d, got=%d",
				len(test.expectedInstructions), len(instructions))
		}

		for i, instruct := range instructions {
			if instruct != test.expectedInstructions[i] {
				t.Errorf("instruct not equal to expect. want=%c, got=%c", test.expectedInstructions[i], instruct)
			}
		}
	}
}
