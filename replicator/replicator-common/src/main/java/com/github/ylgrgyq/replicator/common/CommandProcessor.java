package com.github.ylgrgyq.replicator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommandProcessor implements Processor<RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger("processor");

    private Map<MessageType, Processor<? super RequestCommand>> requestProcessor;
    private Map<MessageType, Processor<? super ResponseCommand>> responseProcessor;
    private Processor<? super RequestCommand> defaultRequestProcessor;
    private Processor<? super ResponseCommand> defaultResponseProcessor;

    public CommandProcessor() {
        this.requestProcessor = new HashMap<>();
        this.responseProcessor = new HashMap<>();
        this.defaultRequestProcessor = cmd ->
                logger.warn("No processor available to process request: %s with type: %s", cmd, cmd.getMessageType());
        this.defaultResponseProcessor = cmd ->
                logger.warn("No processor available to process response: %s with type: %s", cmd, cmd.getMessageType());
    }

    public void registerRequestProcessor(MessageType type, Processor<RequestCommand> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        requestProcessor.put(type, processor);
    }

    public void registerResponseProcessor(MessageType type, Processor<ResponseCommand> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        responseProcessor.put(type, processor);
    }

    public void registerDefaultRequestProcessor(Processor<RequestCommand> processor) {
        Objects.requireNonNull(processor);
        defaultRequestProcessor = processor;
    }

    public void registerDefaultResponseProcessor(Processor<ResponseCommand> processor) {
        Objects.requireNonNull(processor);
        defaultResponseProcessor = processor;
    }

    @Override
    public void process(RemotingCommand cmd) {
        switch (cmd.getCommandType()) {
            case REQUEST:
            case ONE_WAY:
                Processor<? super RequestCommand> reqProcessor = requestProcessor.get(cmd.getMessageType());
                if (reqProcessor != null) {
                    reqProcessor.process((RequestCommand) cmd);
                } else {
                    defaultRequestProcessor.process((RequestCommand) cmd);
                }
                break;
            case RESPONSE:
                Processor<? super ResponseCommand> resProcessor = responseProcessor.get(cmd.getMessageType());
                if (resProcessor != null) {
                    resProcessor.process((ResponseCommand) cmd);
                } else {
                    defaultResponseProcessor.process((ResponseCommand) cmd);
                }
                break;
        }
    }
}
