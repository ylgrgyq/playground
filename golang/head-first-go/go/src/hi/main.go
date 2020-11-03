package main

import (
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
)

func main() {
	ch := make(chan int)
	go responseSize("http://www.baidu.com", ch)
	go responseSize("http://www.taobao.com", ch)
	go responseSize("http://example.com", ch)

	fmt.Println("1", <-ch)
	fmt.Println("2", <-ch)
	fmt.Println("3", <-ch)
}

func responseSize(url string, ch chan int) {
	fmt.Println("Getting", url)
	res, err := http.Get(url)
	if err != nil {
		log.Fatal(err)
	}

	defer res.Body.Close()
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(len(body))
	ch <- len(body)
}
