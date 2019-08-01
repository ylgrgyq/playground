package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.exception.CodecException;

public interface Serializer {
    <T extends RequestCommand> boolean serialize(T cmd) throws CodecException;

    <T extends RequestCommand> boolean deserialize(T cmd) throws CodecException;

    <T extends ResponseCommand> boolean serialize(T cmd) throws CodecException;

    <T extends ResponseCommand> boolean deserialize(T cmd) throws CodecException;
}
