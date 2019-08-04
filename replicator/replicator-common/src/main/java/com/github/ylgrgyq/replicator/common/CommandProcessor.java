package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.protocol.v1.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommandProcessor<T extends Context> implements Processor<T, RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger("processor");

    private Map<MessageType, Processor<? super T, ?>> requestProcessor;
    private Map<MessageType, Processor<? super T, ?>> responseProcessor;
    private Processor<? super T, ?> defaultRequestProcessor;
    private Processor<? super T, ?> defaultResponseProcessor;

    public CommandProcessor() {
        this.requestProcessor = new HashMap<>();
        this.responseProcessor = new HashMap<>();
        this.defaultRequestProcessor = (ctx, obj) ->
                logger.warn("No processor available to process request: {} with type: {}", ctx, ctx.getRemotingCommandMessageType());
        this.defaultResponseProcessor = (ctx, obj) ->
                logger.warn("No processor available to process response: {} with type: {}", ctx, ctx.getRemotingCommandMessageType());
    }

    public void registerRequestProcessor(MessageType type, Processor<? super T, ?> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        requestProcessor.put(type, processor);
    }

    public void registerOnewayCommandProcessor(MessageType type, Processor<? super T, ?> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        requestProcessor.put(type, processor);
    }

    public void registerResponseProcessor(MessageType type, Processor<? super T, ?> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        responseProcessor.put(type, processor);
    }

    public void registerDefaultRequestProcessor(Processor<? super Context, ?> processor) {
        Objects.requireNonNull(processor);
        defaultRequestProcessor = processor;
    }

    public void registerDefaultResponseProcessor(Processor<? super Context, ?> processor) {
        Objects.requireNonNull(processor);
        defaultResponseProcessor = processor;
    }

    @Override
    public void process(T ctx, RemotingCommand cmd) {
        switch (cmd.getCommandType()) {
            case REQUEST:
            case ONE_WAY:
                Processor<? super T, ?> reqProcessor = requestProcessor.get(cmd.getMessageType());
                if (reqProcessor != null) {
                    reqProcessor.process(ctx, cmd.getBody());
                } else {
                    defaultRequestProcessor.process(ctx, cmd.getBody());
                }
                break;
            case RESPONSE:
                Processor<? super T, ?> resProcessor = responseProcessor.get(cmd.getMessageType());
                if (resProcessor != null) {
                    resProcessor.process(ctx, cmd.getBody());
                } else {
                    defaultResponseProcessor.process(ctx, cmd.getBody());
                }
                break;
            default:
                logger.error("Receive unknown command type: {} of remoting command: {}", cmd.getCommandType(), cmd);
                break;
        }
    }
}
