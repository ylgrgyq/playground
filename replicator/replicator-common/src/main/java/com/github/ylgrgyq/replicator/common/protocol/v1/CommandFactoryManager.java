package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.CommandFactory;
import com.github.ylgrgyq.replicator.common.RemotingCommand;
import com.github.ylgrgyq.replicator.common.RequestCommand;
import com.github.ylgrgyq.replicator.common.ResponseCommand;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.*;
import java.util.IdentityHashMap;
import java.util.NoSuchElementException;
import java.util.Set;

public class CommandFactoryManager {
    private static final Logger logger = LoggerFactory.getLogger("protocol-v1");

    private static final IdentityHashMap<MessageType, CommandFactory<? extends RequestCommand>> registeredRequestCommands = new IdentityHashMap<>();
    private static final IdentityHashMap<MessageType, CommandFactory<? extends ResponseCommand>> registeredResponseCommands = new IdentityHashMap<>();

    static {
        ClassLoader loader = CommandFactoryManager.class.getClassLoader();
        Reflections reflections = new Reflections("com.github.ylgrgyq.replicator.common.protocol");
        Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(AutoLoad.class);
        for (Class<?> clazz : annotated) {
            logger.warn("got class {}", clazz.getName());
            try {
                Class<?> loadC = loader.loadClass(clazz.getName());
                loadC.newInstance();
            } catch (Exception ex) {
                logger.error("load {} failed", clazz.getName(), ex);
            }
        }
    }

    public static void registerRequestCommand(MessageType type, CommandFactory<? extends RequestCommand> factory) {
        registeredRequestCommands.put(type, factory);
    }

    public static void registerResponseCommand(MessageType type, CommandFactory<? extends ResponseCommand> factory) {
        registeredResponseCommands.put(type, factory);
    }

    public static CommandFactory<? extends RequestCommand> getRequestCommandFactory(MessageType type) {
        return registeredRequestCommands.get(type);
    }

    public static CommandFactory<? extends ResponseCommand> getResponseCommandFactory(MessageType type) {
        return registeredResponseCommands.get(type);
    }

    public static ResponseCommand createResponse(RemotingCommand request) {
        CommandFactory<? extends ResponseCommand> factory = getResponseCommandFactory(request.getMessageType());
        if (factory != null) {
            return factory.createCommand();
        } else {
            throw new NoSuchElementException();
        }
    }

    public static RequestCommand createRequest(MessageType type) {
        CommandFactory<? extends RequestCommand> factory = getRequestCommandFactory(type);
        if (factory != null) {
            RequestCommand req = factory.createCommand();
            req.setMessageVersion(MessageType.VERSION);
            return req;
        } else {
            logger.warn("hahah {} {}", registeredRequestCommands, registeredResponseCommands);
            String emsg = "No command factory for msg type:" + type.name();
            throw new NoSuchElementException(emsg);
        }
    }

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface AutoLoad {
        // no value
    }
}
