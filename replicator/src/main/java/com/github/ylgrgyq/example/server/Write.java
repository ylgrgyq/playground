package com.github.ylgrgyq.example.server;

import com.github.ylgrgyq.server.SequenceGroups;
import com.github.ylgrgyq.server.Sequence;
import com.github.ylgrgyq.server.SequenceOptions;

public class Write {
    public static void main(String[] args) {
        SequenceGroups server = new SequenceGroups();
        SequenceOptions options = new SequenceOptions();
        Sequence sequence = server.createSequence("haha", options);
        sequence.append("111".getBytes());
        sequence.append("222".getBytes());




    }
}
