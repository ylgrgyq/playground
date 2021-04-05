package swimmer

import "testing"

func TestJoin(t *testing.T) {
	config := Config{Endpoint: &Endpoint{Name: "A"}}
	eg, err := CreateEndpointGroup(&config)
	if err != nil {
		panic(err)
	}

	eg.Join(&Endpoint{Name: "B"})
}