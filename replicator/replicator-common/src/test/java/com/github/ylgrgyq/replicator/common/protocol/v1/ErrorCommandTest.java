package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ErrorCommandTest {

    @Test
    public void serializeNormal() throws DeserializationException {
        ErrorCommand expect = new ErrorCommand();
        expect.setErrorMsg("Hello");
        expect.setErrorCode(10101);

        expect.serialize();

        ErrorCommand actual = new ErrorCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }

    @Test
    public void serializeNoErrorMsg() throws DeserializationException {
        ErrorCommand expect = new ErrorCommand();
        expect.setErrorCode(10101);

        expect.serialize();

        ErrorCommand actual = new ErrorCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
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