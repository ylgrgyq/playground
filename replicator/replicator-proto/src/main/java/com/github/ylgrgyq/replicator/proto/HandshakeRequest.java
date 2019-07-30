// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: commands.proto

package com.github.ylgrgyq.replicator.proto;

/**
 * Protobuf type {@code com.github.ylgrgyq.replicator.proto.HandshakeRequest}
 */
public  final class HandshakeRequest extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:com.github.ylgrgyq.replicator.proto.HandshakeRequest)
    HandshakeRequestOrBuilder {
  // Use HandshakeRequest.newBuilder() to construct.
  private HandshakeRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private HandshakeRequest() {
    topic_ = "";
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
  }
  private HandshakeRequest(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    int mutable_bitField0_ = 0;
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          default: {
            if (!input.skipField(tag)) {
              done = true;
            }
            break;
          }
          case 10: {
            java.lang.String s = input.readStringRequireUtf8();

            topic_ = s;
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_descriptor;
  }

  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.github.ylgrgyq.replicator.proto.HandshakeRequest.class, com.github.ylgrgyq.replicator.proto.HandshakeRequest.Builder.class);
  }

  public static final int TOPIC_FIELD_NUMBER = 1;
  private volatile java.lang.Object topic_;
  /**
   * <code>optional string topic = 1;</code>
   */
  public java.lang.String getTopic() {
    java.lang.Object ref = topic_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      topic_ = s;
      return s;
    }
  }
  /**
   * <code>optional string topic = 1;</code>
   */
  public com.google.protobuf.ByteString
      getTopicBytes() {
    java.lang.Object ref = topic_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      topic_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  private byte memoizedIsInitialized = -1;
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (!getTopicBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, topic_);
    }
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getTopicBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, topic_);
    }
    memoizedSize = size;
    return size;
  }

  private static final long serialVersionUID = 0L;
  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof com.github.ylgrgyq.replicator.proto.HandshakeRequest)) {
      return super.equals(obj);
    }
    com.github.ylgrgyq.replicator.proto.HandshakeRequest other = (com.github.ylgrgyq.replicator.proto.HandshakeRequest) obj;

    boolean result = true;
    result = result && getTopic()
        .equals(other.getTopic());
    return result;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptorForType().hashCode();
    hash = (37 * hash) + TOPIC_FIELD_NUMBER;
    hash = (53 * hash) + getTopic().hashCode();
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.github.ylgrgyq.replicator.proto.HandshakeRequest prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code com.github.ylgrgyq.replicator.proto.HandshakeRequest}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:com.github.ylgrgyq.replicator.proto.HandshakeRequest)
      com.github.ylgrgyq.replicator.proto.HandshakeRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_descriptor;
    }

    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.github.ylgrgyq.replicator.proto.HandshakeRequest.class, com.github.ylgrgyq.replicator.proto.HandshakeRequest.Builder.class);
    }

    // Construct using com.github.ylgrgyq.replicator.proto.HandshakeRequest.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
      }
    }
    public Builder clear() {
      super.clear();
      topic_ = "";

      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_descriptor;
    }

    public com.github.ylgrgyq.replicator.proto.HandshakeRequest getDefaultInstanceForType() {
      return com.github.ylgrgyq.replicator.proto.HandshakeRequest.getDefaultInstance();
    }

    public com.github.ylgrgyq.replicator.proto.HandshakeRequest build() {
      com.github.ylgrgyq.replicator.proto.HandshakeRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public com.github.ylgrgyq.replicator.proto.HandshakeRequest buildPartial() {
      com.github.ylgrgyq.replicator.proto.HandshakeRequest result = new com.github.ylgrgyq.replicator.proto.HandshakeRequest(this);
      result.topic_ = topic_;
      onBuilt();
      return result;
    }

    public Builder clone() {
      return (Builder) super.clone();
    }
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        Object value) {
      return (Builder) super.setField(field, value);
    }
    public Builder clearField(
        com.google.protobuf.Descriptors.FieldDescriptor field) {
      return (Builder) super.clearField(field);
    }
    public Builder clearOneof(
        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return (Builder) super.clearOneof(oneof);
    }
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        int index, Object value) {
      return (Builder) super.setRepeatedField(field, index, value);
    }
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        Object value) {
      return (Builder) super.addRepeatedField(field, value);
    }
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.github.ylgrgyq.replicator.proto.HandshakeRequest) {
        return mergeFrom((com.github.ylgrgyq.replicator.proto.HandshakeRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.github.ylgrgyq.replicator.proto.HandshakeRequest other) {
      if (other == com.github.ylgrgyq.replicator.proto.HandshakeRequest.getDefaultInstance()) return this;
      if (!other.getTopic().isEmpty()) {
        topic_ = other.topic_;
        onChanged();
      }
      onChanged();
      return this;
    }

    public final boolean isInitialized() {
      return true;
    }

    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      com.github.ylgrgyq.replicator.proto.HandshakeRequest parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.github.ylgrgyq.replicator.proto.HandshakeRequest) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object topic_ = "";
    /**
     * <code>optional string topic = 1;</code>
     */
    public java.lang.String getTopic() {
      java.lang.Object ref = topic_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        topic_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string topic = 1;</code>
     */
    public com.google.protobuf.ByteString
        getTopicBytes() {
      java.lang.Object ref = topic_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        topic_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string topic = 1;</code>
     */
    public Builder setTopic(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      topic_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string topic = 1;</code>
     */
    public Builder clearTopic() {
      
      topic_ = getDefaultInstance().getTopic();
      onChanged();
      return this;
    }
    /**
     * <code>optional string topic = 1;</code>
     */
    public Builder setTopicBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      topic_ = value;
      onChanged();
      return this;
    }
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return this;
    }

    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return this;
    }


    // @@protoc_insertion_point(builder_scope:com.github.ylgrgyq.replicator.proto.HandshakeRequest)
  }

  // @@protoc_insertion_point(class_scope:com.github.ylgrgyq.replicator.proto.HandshakeRequest)
  private static final com.github.ylgrgyq.replicator.proto.HandshakeRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.github.ylgrgyq.replicator.proto.HandshakeRequest();
  }

  public static com.github.ylgrgyq.replicator.proto.HandshakeRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<HandshakeRequest>
      PARSER = new com.google.protobuf.AbstractParser<HandshakeRequest>() {
    public HandshakeRequest parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return new HandshakeRequest(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<HandshakeRequest> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<HandshakeRequest> getParserForType() {
    return PARSER;
  }

  public com.github.ylgrgyq.replicator.proto.HandshakeRequest getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

