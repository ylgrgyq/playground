package com.github.ylgrgyq.replicator.common.commands;

import com.github.ylgrgyq.replicator.common.CommandFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.IdentityHashMap;

public class CommandFactoryManager {
    private static final Logger logger = LoggerFactory.getLogger("protocol-v1");

    private static final IdentityHashMap<MessageType, CommandFactory<? extends RequestCommand>> registeredRequestCommands = new IdentityHashMap<>();
    private static final IdentityHashMap<MessageType, CommandFactory<? extends ResponseCommand>> registeredResponseCommands = new IdentityHashMap<>();

    static {
        registerRequestCommand(MessageType.ERROR, ErrorCommand::new);
        registerRequestCommand(MessageType.FETCH_LOGS, FetchLogsRequestCommand::new);
        registerRequestCommand(MessageType.FETCH_SNAPSHOT, FetchSnapshotRequestCommand::new);
        registerRequestCommand(MessageType.HANDSHAKE, HandshakeRequestCommand::new);

        registerResponseCommand(MessageType.FETCH_LOGS, FetchLogsResponseCommand::new);
        registerResponseCommand(MessageType.FETCH_SNAPSHOT, FetchSnapshotResponseCommand::new);
        registerResponseCommand(MessageType.HANDSHAKE, HandshakeResponseCommand::new);
    }

    private static void registerRequestCommand(MessageType type, CommandFactory<? extends RequestCommand> factory) {
        registeredRequestCommands.put(type, factory);
    }

    private static void registerResponseCommand(MessageType type, CommandFactory<? extends ResponseCommand> factory) {
        registeredResponseCommands.put(type, factory);
    }

    private static CommandFactory<? extends RequestCommand> getRequestCommandFactory(MessageType type) {
        return registeredRequestCommands.get(type);
    }

    private static CommandFactory<? extends ResponseCommand> getResponseCommandFactory(MessageType type) {
        return registeredResponseCommands.get(type);
    }

    public static RequestCommand createRequest(MessageType type) {
        CommandFactory<? extends RequestCommand> factory = getRequestCommandFactory(type);
        if (factory != null) {
            return factory.createCommand();
        } else {
            String emsg = "No request command factory registered for message type: " + type.name();
            logger.error(emsg);
            throw new RuntimeException(emsg);
        }
    }

    public static <T extends ResponseCommand> T createResponse(RemotingCommand request) {
        MessageType type = request.getMessageType();
        T res =  createResponse(type);
        res.setMessageVersion(request.getMessageVersion());
        return res;
    }

    @SuppressWarnings("unchecked")
    public static <T extends ResponseCommand> T createResponse(MessageType type) {
        CommandFactory<? extends ResponseCommand> factory = getResponseCommandFactory(type);
        if (factory != null) {
            return (T)factory.createCommand();
        } else {
            String emsg = "No response command factory registered for message type: " + type.name();
            logger.error(emsg);
            throw new RuntimeException(emsg);
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface AutoLoad {
        // no value
    }
}
