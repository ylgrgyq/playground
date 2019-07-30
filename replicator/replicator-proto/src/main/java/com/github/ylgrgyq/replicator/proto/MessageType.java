// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: commands.proto

package com.github.ylgrgyq.replicator.proto;

/**
 * Protobuf enum {@code com.github.ylgrgyq.replicator.proto.MessageType}
 */
public enum MessageType
    implements com.google.protobuf.ProtocolMessageEnum {
  /**
   * <code>ONE_WAY = 0;</code>
   */
  ONE_WAY(0),
  /**
   * <code>REQUEST = 1;</code>
   */
  REQUEST(1),
  /**
   * <code>RESPONSE = 2;</code>
   */
  RESPONSE(2),
  UNRECOGNIZED(-1),
  ;

  /**
   * <code>ONE_WAY = 0;</code>
   */
  public static final int ONE_WAY_VALUE = 0;
  /**
   * <code>REQUEST = 1;</code>
   */
  public static final int REQUEST_VALUE = 1;
  /**
   * <code>RESPONSE = 2;</code>
   */
  public static final int RESPONSE_VALUE = 2;


  public final int getNumber() {
    if (this == UNRECOGNIZED) {
      throw new java.lang.IllegalArgumentException(
          "Can't get the number of an unknown enum value.");
    }
    return value;
  }

  /**
   * @deprecated Use {@link #forNumber(int)} instead.
   */
  @java.lang.Deprecated
  public static MessageType valueOf(int value) {
    return forNumber(value);
  }

  public static MessageType forNumber(int value) {
    switch (value) {
      case 0: return ONE_WAY;
      case 1: return REQUEST;
      case 2: return RESPONSE;
      default: return null;
    }
  }

  public static com.google.protobuf.Internal.EnumLiteMap<MessageType>
      internalGetValueMap() {
    return internalValueMap;
  }
  private static final com.google.protobuf.Internal.EnumLiteMap<
      MessageType> internalValueMap =
        new com.google.protobuf.Internal.EnumLiteMap<MessageType>() {
          public MessageType findValueByNumber(int number) {
            return MessageType.forNumber(number);
          }
        };

  public final com.google.protobuf.Descriptors.EnumValueDescriptor
      getValueDescriptor() {
    return getDescriptor().getValues().get(ordinal());
  }
  public final com.google.protobuf.Descriptors.EnumDescriptor
      getDescriptorForType() {
    return getDescriptor();
  }
  public static final com.google.protobuf.Descriptors.EnumDescriptor
      getDescriptor() {
    return com.github.ylgrgyq.replicator.proto.Commands.getDescriptor()
        .getEnumTypes().get(1);
  }

  private static final MessageType[] VALUES = values();

  public static MessageType valueOf(
      com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
    if (desc.getType() != getDescriptor()) {
      throw new java.lang.IllegalArgumentException(
        "EnumValueDescriptor is not for this type.");
    }
    if (desc.getIndex() == -1) {
      return UNRECOGNIZED;
    }
    return VALUES[desc.getIndex()];
  }

  private final int value;

  private MessageType(int value) {
    this.value = value;
  }

  // @@protoc_insertion_point(enum_scope:com.github.ylgrgyq.replicator.proto.MessageType)
}

