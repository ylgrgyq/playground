package com.github.ylgrgyq.example.server;

import com.github.ylgrgyq.server.ReplicatorServer;
import com.github.ylgrgyq.server.Sequence;
import com.github.ylgrgyq.server.SequenceOptions;

public class Write {
    public static void main(String[] args) {
        ReplicatorServer server = new ReplicatorServer();
        SequenceOptions options = new SequenceOptions();
        Sequence sequence = server.createSequence("haha", options);
        sequence.append("111".getBytes());
        sequence.append("222".getBytes());





    }
}
