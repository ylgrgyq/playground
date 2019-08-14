package com.github.ylgrgyq.resender;

import java.util.concurrent.BlockingDeque;

public interface BackupQueue<E extends BackupPayload> extends BlockingDeque<E> {
    void persistent();
    void persistent(boolean force);
}
