package com.github.ylgrgyq.replicator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommandProcessor implements Processor<RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger("processor");

    private Map<MessageType, Processor<?>> requestProcessor;
    private Map<MessageType, Processor<?>> responseProcessor;
    private Processor<?> defaultRequestProcessor;
    private Processor<?> defaultResponseProcessor;

    public CommandProcessor() {
        this.requestProcessor = new HashMap<>();
        this.responseProcessor = new HashMap<>();
        this.defaultRequestProcessor = (ctx, obj) ->
                logger.warn("No processor available to process request: {} with type: {}", ctx, ctx.getMessageType());
        this.defaultResponseProcessor = (ctx, obj) ->
                logger.warn("No processor available to process response: {} with type: {}", ctx, ctx.getMessageType());
    }

    public void registerRequestProcessor(MessageType type, Processor<?> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        requestProcessor.put(type, processor);
    }

    public void registerResponseProcessor(MessageType type, Processor<?> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        responseProcessor.put(type, processor);
    }

    public void registerDefaultRequestProcessor(Processor<?> processor) {
        Objects.requireNonNull(processor);
        defaultRequestProcessor = processor;
    }

    public void registerDefaultResponseProcessor(Processor<?> processor) {
        Objects.requireNonNull(processor);
        defaultResponseProcessor = processor;
    }

    @Override
    public void process(Context ctx, RemotingCommand cmd) {
        switch (cmd.getCommandType()) {
            case REQUEST:
            case ONE_WAY:
                Processor<?> reqProcessor = requestProcessor.get(cmd.getMessageType());
                if (reqProcessor != null) {
                    reqProcessor.process(ctx, cmd.getBody());
                } else {
                    defaultRequestProcessor.process(ctx, cmd.getBody());
                }
                break;
            case RESPONSE:
                Processor<?> resProcessor = responseProcessor.get(cmd.getMessageType());
                if (resProcessor != null) {
                    resProcessor.process(ctx, cmd.getBody());
                } else {
                    defaultResponseProcessor.process(ctx, cmd.getBody());
                }
                break;
        }
    }
}
