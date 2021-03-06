package com.github.ylgrgyq.replicator.common.commands;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.common.exception.SerializationException;

import java.util.Objects;

public abstract class RemotingCommand {
    private final CommandType commandType;

    private byte messageVersion;
    private byte[] content;
    private int contentLength;

    protected RemotingCommand(CommandType commandType, byte defaultMsgVersion) {
        this.commandType = commandType;
        this.messageVersion = defaultMsgVersion;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public abstract MessageType getMessageType();

    public byte getMessageVersion() {
        return messageVersion;
    }

    public void setMessageVersion(byte messageVersion) {
        this.messageVersion = messageVersion;
    }

    public byte[] getContent() {
        return content;
    }

    protected void setContent(byte[] content) {
        this.content = content;
        if (content != null) {
            this.contentLength = content.length;
        }
    }

    public int getContentLength() {
        return contentLength;
    }

    @SuppressWarnings("unchecked")
    public <T extends RemotingCommand> T cast() {
        return (T)this;
    }

    public abstract void serialize() throws SerializationException;

    public abstract void deserialize(byte[] content) throws DeserializationException;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemotingCommand that = (RemotingCommand) o;
        return getMessageVersion() == that.getMessageVersion() &&
                getCommandType() == that.getCommandType() &&
                getMessageType() == that.getMessageType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCommandType(), getMessageType(), getMessageVersion());
    }

    @Override
    public String toString() {
        return ", messageVersion=" + messageVersion +
                ", contentLength=" + contentLength;
    }
}
