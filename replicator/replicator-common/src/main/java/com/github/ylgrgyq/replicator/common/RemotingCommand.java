package com.github.ylgrgyq.replicator.common;

public abstract class RemotingCommand {
    private CommandType commandType;
    private MessageType messageType;
    private byte messageVersion;

    private byte[] content;
    private int contentLength;
    private Object body;

    protected RemotingCommand(CommandType commandType) {
        this.commandType = commandType;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public void setCommandType(CommandType commandType) {
        this.commandType = commandType;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public byte getMessageVersion() {
        return messageVersion;
    }

    public void setMessageVersion(byte messageVersion) {
        this.messageVersion = messageVersion;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
        if (content != null) {
            this.contentLength = content.length;
        }
    }

    public int getContentLength() {
        return contentLength;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBody(){
        return (T)body;
    }

    protected void setBody(Object body) {
        this.body = body;
    }

    abstract void serialize();

    abstract void deserialize() ;
}
