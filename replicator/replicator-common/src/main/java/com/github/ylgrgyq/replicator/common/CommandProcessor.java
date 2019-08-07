package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;
import com.github.ylgrgyq.replicator.common.commands.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommandProcessor<CXT extends Context> implements Processor<CXT, RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger("processor");

    private Map<MessageType, Processor<? super CXT, ? extends RemotingCommand>> requestProcessor;
    private Map<MessageType, Processor<? super CXT, ? extends RemotingCommand>> responseProcessor;
    private Processor<? super CXT, RemotingCommand> defaultRequestProcessor;
    private Processor<? super CXT, RemotingCommand> defaultResponseProcessor;

    public CommandProcessor() {
        this.requestProcessor = new HashMap<>();
        this.responseProcessor = new HashMap<>();
        this.defaultRequestProcessor = (Context ctx, RemotingCommand obj) ->
                logger.warn("No processor available to process request: {} with type: {}", ctx, ctx.getRemotingCommandMessageType());
        this.defaultResponseProcessor = (Context ctx, RemotingCommand obj) ->
                logger.warn("No processor available to process response: {} with type: {}", ctx, ctx.getRemotingCommandMessageType());
    }

    public void registerRequestProcessor(MessageType type, Processor<? super CXT, ? extends RemotingCommand> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        requestProcessor.put(type, processor);
    }

    public void registerOnewayCommandProcessor(MessageType type, Processor<? super CXT, ? extends RemotingCommand> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        requestProcessor.put(type, processor);
    }

    public void registerResponseProcessor(MessageType type, Processor<? super CXT, ? extends RemotingCommand> processor) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(processor);

        responseProcessor.put(type, processor);
    }

    public void registerDefaultRequestProcessor(Processor<? super Context, RemotingCommand> processor) {
        Objects.requireNonNull(processor);
        defaultRequestProcessor = processor;
    }

    public void registerDefaultResponseProcessor(Processor<? super Context, RemotingCommand> processor) {
        Objects.requireNonNull(processor);
        defaultResponseProcessor = processor;
    }

    @Override
    public void process(CXT ctx, RemotingCommand cmd) {
        switch (cmd.getCommandType()) {
            case REQUEST:
            case ONE_WAY:
                Processor<? super CXT, ? extends RemotingCommand> reqProcessor = requestProcessor.get(cmd.getMessageType());
                if (reqProcessor != null) {
                    reqProcessor.process(ctx, cmd.cast());
                } else {
                    defaultRequestProcessor.process(ctx, cmd);
                }
                break;
            case RESPONSE:
                Processor<? super CXT, ? extends RemotingCommand> resProcessor = responseProcessor.get(cmd.getMessageType());
                if (resProcessor != null) {
                    resProcessor.process(ctx, cmd.cast());
                } else {
                    defaultResponseProcessor.process(ctx, cmd);
                }
                break;
            default:
                logger.error("Receive unknown command type: {} of remoting command: {}", cmd.getCommandType(), cmd);
                break;
        }
    }
}
