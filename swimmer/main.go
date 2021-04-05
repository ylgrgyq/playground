package swimmer

type Ha struct {
	member bool
}

func main() {
	config := Config{Endpoint: &Endpoint{Name: "A"}}
	eg1, err := CreateEndpointGroup(&config)
	if err != nil {
		panic(err)
	}

	config2 := Config{Endpoint: &Endpoint{Name: "B"}}
	eg2, err := CreateEndpointGroup(&config2)
	if err != nil {
		panic(err)
	}

	eg1.Join(config.Endpoint)


}
