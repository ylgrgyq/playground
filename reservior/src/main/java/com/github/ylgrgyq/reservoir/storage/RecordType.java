package com.github.ylgrgyq.reservoir.storage;

import javax.annotation.Nullable;

/**
 * Author: ylgrgyq
 * Date: 18/6/10
 */
public enum RecordType {
    // Zero is reserved for preallocated files
    kZeroType((byte)0),

    kFullType((byte)1),

    // For fragments
    kFirstType((byte)2),
    kMiddleType((byte)3),
    kLastType((byte)4),

    // EOF
    kEOF((byte)5);

    private final byte code;

    RecordType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    @Nullable
    public static RecordType getRecordTypeByCode(byte code) {
        for (RecordType type: RecordType.values()) {
            if (type.getCode() == code) {
                return type;
            }
        }

        return null;
    }
}
