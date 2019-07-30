// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: commands.proto

package com.github.ylgrgyq.replicator.proto;

/**
 * Protobuf type {@code com.github.ylgrgyq.replicator.proto.FetchLogsRequest}
 */
public  final class FetchLogsRequest extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:com.github.ylgrgyq.replicator.proto.FetchLogsRequest)
    FetchLogsRequestOrBuilder {
  // Use FetchLogsRequest.newBuilder() to construct.
  private FetchLogsRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private FetchLogsRequest() {
    fromId_ = 0L;
    limit_ = 0;
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
  }
  private FetchLogsRequest(
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
          case 8: {

            fromId_ = input.readInt64();
            break;
          }
          case 16: {

            limit_ = input.readInt32();
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
    return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_descriptor;
  }

  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.github.ylgrgyq.replicator.proto.FetchLogsRequest.class, com.github.ylgrgyq.replicator.proto.FetchLogsRequest.Builder.class);
  }

  public static final int FROMID_FIELD_NUMBER = 1;
  private long fromId_;
  /**
   * <code>optional int64 fromId = 1;</code>
   */
  public long getFromId() {
    return fromId_;
  }

  public static final int LIMIT_FIELD_NUMBER = 2;
  private int limit_;
  /**
   * <code>optional int32 limit = 2;</code>
   */
  public int getLimit() {
    return limit_;
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
    if (fromId_ != 0L) {
      output.writeInt64(1, fromId_);
    }
    if (limit_ != 0) {
      output.writeInt32(2, limit_);
    }
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (fromId_ != 0L) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(1, fromId_);
    }
    if (limit_ != 0) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt32Size(2, limit_);
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
    if (!(obj instanceof com.github.ylgrgyq.replicator.proto.FetchLogsRequest)) {
      return super.equals(obj);
    }
    com.github.ylgrgyq.replicator.proto.FetchLogsRequest other = (com.github.ylgrgyq.replicator.proto.FetchLogsRequest) obj;

    boolean result = true;
    result = result && (getFromId()
        == other.getFromId());
    result = result && (getLimit()
        == other.getLimit());
    return result;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptorForType().hashCode();
    hash = (37 * hash) + FROMID_FIELD_NUMBER;
    hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
        getFromId());
    hash = (37 * hash) + LIMIT_FIELD_NUMBER;
    hash = (53 * hash) + getLimit();
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest parseFrom(
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
  public static Builder newBuilder(com.github.ylgrgyq.replicator.proto.FetchLogsRequest prototype) {
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
   * Protobuf type {@code com.github.ylgrgyq.replicator.proto.FetchLogsRequest}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:com.github.ylgrgyq.replicator.proto.FetchLogsRequest)
      com.github.ylgrgyq.replicator.proto.FetchLogsRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_descriptor;
    }

    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.github.ylgrgyq.replicator.proto.FetchLogsRequest.class, com.github.ylgrgyq.replicator.proto.FetchLogsRequest.Builder.class);
    }

    // Construct using com.github.ylgrgyq.replicator.proto.FetchLogsRequest.newBuilder()
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
      fromId_ = 0L;

      limit_ = 0;

      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.github.ylgrgyq.replicator.proto.Commands.internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_descriptor;
    }

    public com.github.ylgrgyq.replicator.proto.FetchLogsRequest getDefaultInstanceForType() {
      return com.github.ylgrgyq.replicator.proto.FetchLogsRequest.getDefaultInstance();
    }

    public com.github.ylgrgyq.replicator.proto.FetchLogsRequest build() {
      com.github.ylgrgyq.replicator.proto.FetchLogsRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public com.github.ylgrgyq.replicator.proto.FetchLogsRequest buildPartial() {
      com.github.ylgrgyq.replicator.proto.FetchLogsRequest result = new com.github.ylgrgyq.replicator.proto.FetchLogsRequest(this);
      result.fromId_ = fromId_;
      result.limit_ = limit_;
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
      if (other instanceof com.github.ylgrgyq.replicator.proto.FetchLogsRequest) {
        return mergeFrom((com.github.ylgrgyq.replicator.proto.FetchLogsRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.github.ylgrgyq.replicator.proto.FetchLogsRequest other) {
      if (other == com.github.ylgrgyq.replicator.proto.FetchLogsRequest.getDefaultInstance()) return this;
      if (other.getFromId() != 0L) {
        setFromId(other.getFromId());
      }
      if (other.getLimit() != 0) {
        setLimit(other.getLimit());
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
      com.github.ylgrgyq.replicator.proto.FetchLogsRequest parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.github.ylgrgyq.replicator.proto.FetchLogsRequest) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private long fromId_ ;
    /**
     * <code>optional int64 fromId = 1;</code>
     */
    public long getFromId() {
      return fromId_;
    }
    /**
     * <code>optional int64 fromId = 1;</code>
     */
    public Builder setFromId(long value) {
      
      fromId_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional int64 fromId = 1;</code>
     */
    public Builder clearFromId() {
      
      fromId_ = 0L;
      onChanged();
      return this;
    }

    private int limit_ ;
    /**
     * <code>optional int32 limit = 2;</code>
     */
    public int getLimit() {
      return limit_;
    }
    /**
     * <code>optional int32 limit = 2;</code>
     */
    public Builder setLimit(int value) {
      
      limit_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional int32 limit = 2;</code>
     */
    public Builder clearLimit() {
      
      limit_ = 0;
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


    // @@protoc_insertion_point(builder_scope:com.github.ylgrgyq.replicator.proto.FetchLogsRequest)
  }

  // @@protoc_insertion_point(class_scope:com.github.ylgrgyq.replicator.proto.FetchLogsRequest)
  private static final com.github.ylgrgyq.replicator.proto.FetchLogsRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.github.ylgrgyq.replicator.proto.FetchLogsRequest();
  }

  public static com.github.ylgrgyq.replicator.proto.FetchLogsRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<FetchLogsRequest>
      PARSER = new com.google.protobuf.AbstractParser<FetchLogsRequest>() {
    public FetchLogsRequest parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return new FetchLogsRequest(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<FetchLogsRequest> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<FetchLogsRequest> getParserForType() {
    return PARSER;
  }

  public com.github.ylgrgyq.replicator.proto.FetchLogsRequest getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

