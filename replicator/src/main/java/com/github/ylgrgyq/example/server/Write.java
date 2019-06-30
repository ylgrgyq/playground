package com.github.ylgrgyq.example.server;

import com.github.ylgrgyq.server.ReplicatorServer;
import com.github.ylgrgyq.server.Source;
import com.github.ylgrgyq.server.SourceOptions;

public class Write {
    public static void main(String[] args) {
        ReplicatorServer server = new ReplicatorServer();
        SourceOptions options = new SourceOptions();
        Source source = server.createSource("haha", options);
        source.append("111".getBytes());
        source.append("222".getBytes());


        


    }
}
