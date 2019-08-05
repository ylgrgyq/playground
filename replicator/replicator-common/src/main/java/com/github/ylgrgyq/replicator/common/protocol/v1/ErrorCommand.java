package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@CommandFactoryManager.AutoLoad
public final class ErrorCommand extends RequestCommandV1 {
    private static final int MINIMUM_LENGTH = 8;

    private int errorCode;
    private String errorMsg;

    public ErrorCommand() {
        super(CommandType.ONE_WAY, MessageType.ERROR);
        this.errorMsg = "";
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMsg(String errorMsg) {
        if (errorMsg != null) {
            this.errorMsg = errorMsg;
        }
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    @Override
    public void serialize() {
        byte[] buffer;
        if (errorMsg == null) {
            buffer = new byte[Integer.BYTES + Integer.BYTES];

            Bits.putInt(buffer, 0, errorCode);
            Bits.putInt(buffer, 4, 0);
        } else {
            byte[] msg = errorMsg.getBytes(StandardCharsets.UTF_8);

            buffer = new byte[Integer.BYTES + Integer.BYTES + msg.length];

            Bits.putInt(buffer, 0, errorCode);
            Bits.putInt(buffer, 4, msg.length);
            System.arraycopy(msg, 0, buffer, 8, msg.length);
        }

        setContent(buffer);
    }

    @Override
    public void deserialize() throws DeserializationException {
        byte[] content = getContent();
        if (content != null && content.length >= MINIMUM_LENGTH) {
            errorCode = Bits.getInt(content, 0);
            int len = Bits.getInt(content, 4);
            if (content.length == MINIMUM_LENGTH + len) {
                byte[] msg = new byte[len];
                System.arraycopy(content, 8, msg, 0, len);
                errorMsg = new String(msg);
            } else {
                throw new DeserializationException("Error msg underflow");
            }
        } else {
            throw new DeserializationException("Error command buffer underflow");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ErrorCommand that = (ErrorCommand) o;
        return getErrorCode() == that.getErrorCode() &&
                Objects.equals(getErrorMsg(), that.getErrorMsg());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getErrorCode(), getErrorMsg());
    }

    @Override
    public String toString() {
        return "{" +
                super.toString() +
                "errorCode=" + errorCode +
                (errorMsg != null ? ", errorMsg='" + errorMsg + '\'' : "") +
                '}';
    }
}
