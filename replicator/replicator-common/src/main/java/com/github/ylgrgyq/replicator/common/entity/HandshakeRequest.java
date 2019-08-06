package com.github.ylgrgyq.replicator.common.entity;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class HandshakeRequest {
    private static final int MINIMUM_LENGTH = 4;

    private String topic;

    public HandshakeRequest() {
        this.topic = "";
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public byte[] serialize() {
        byte[] buffer;
        if (topic != null) {
            byte[] bs = topic.getBytes(StandardCharsets.UTF_8);
            buffer = new byte[Integer.BYTES + bs.length];
            Bits.putInt(buffer, 0, bs.length);
            System.arraycopy(bs, 0, buffer, 4, bs.length);
        } else {
            buffer = new byte[Integer.BYTES];
            Bits.putInt(buffer, 0, 0);
        }

        return buffer;
    }

    public void deserialize(byte[] content) throws DeserializationException {
        if (content != null && content.length >= MINIMUM_LENGTH) {
            int len = Bits.getInt(content, 0);
            if (content.length >= MINIMUM_LENGTH + len) {

                byte[] msg = new byte[len];
                System.arraycopy(content, 4, msg, 0, len);
                topic = new String(msg);
            } else {
                throw new DeserializationException("Handshake request topic underflow");
            }
        } else {
            throw new DeserializationException("Handshake request command buffer underflow");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        HandshakeRequest that = (HandshakeRequest) o;
        return Objects.equals(getTopic(), that.getTopic());
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), getTopic());
    }

    @Override
    public String toString() {
        return "HandshakeRequest{" +
                super.toString() +
                "topic='" + topic + '\'' +
                '}';
    }
}
