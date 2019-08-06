package com.github.ylgrgyq.replicator.common.commands;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ErrorCommandTest {

    @Test
    public void serializeNormal() throws DeserializationException {
        String errorMsg = "Hello";
        int errorCode = 10101;
        ErrorCommand expect = new ErrorCommand();
        expect.setErrorMsg(errorMsg);
        expect.setErrorCode(errorCode);

        expect.serialize();

        ErrorCommand actual = new ErrorCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
        assertEquals(errorCode, actual.getErrorCode());
        assertEquals(errorMsg, actual.getErrorMsg());
    }

    @Test
    public void serializeNoErrorMsg() throws DeserializationException {
        int errorCode = 10101;
        ErrorCommand expect = new ErrorCommand();
        expect.setErrorCode(errorCode);

        expect.serialize();

        ErrorCommand actual = new ErrorCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
        assertEquals(errorCode, actual.getErrorCode());
        assertEquals("", actual.getErrorMsg());
    }

    @Test
    public void serializeEmptyMsg() throws DeserializationException {
        ErrorCommand expect = new ErrorCommand();

        expect.serialize();

        ErrorCommand actual = new ErrorCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }
}