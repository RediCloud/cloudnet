/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dytanic.cloudnet.driver.network.netty.buffer;

import de.dytanic.cloudnet.driver.network.buffer.DataBuf;
import de.dytanic.cloudnet.driver.network.netty.NettyUtils;
import de.dytanic.cloudnet.driver.network.rpc.defaults.object.DefaultObjectMapper;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NettyImmutableDataBuf implements DataBuf {

  protected final ByteBuf byteBuf;
  protected boolean releasable;

  public NettyImmutableDataBuf(ByteBuf byteBuf) {
    this.byteBuf = byteBuf;
    this.enableReleasing();
  }

  @Override
  public boolean readBoolean() {
    return this.hotRead(ByteBuf::readBoolean);
  }

  @Override
  public byte readByte() {
    return this.hotRead(ByteBuf::readByte);
  }

  @Override
  public int readInt() {
    return this.hotRead(ByteBuf::readInt);
  }

  @Override
  public short readShort() {
    return this.hotRead(ByteBuf::readShort);
  }

  @Override
  public long readLong() {
    return this.hotRead(ByteBuf::readLong);
  }

  @Override
  public float readFloat() {
    return this.hotRead(ByteBuf::readFloat);
  }

  @Override
  public double readDouble() {
    return this.hotRead(ByteBuf::readDouble);
  }

  @Override
  public char readChar() {
    return this.hotRead(ByteBuf::readChar);
  }

  @Override
  public byte[] readByteArray() {
    return this.hotRead(buf -> {
      var bytes = new byte[NettyUtils.readVarInt(buf)];
      buf.readBytes(bytes);
      return bytes;
    });
  }

  @Override
  public @NotNull UUID readUniqueId() {
    return new UUID(this.readLong(), this.readLong());
  }

  @Override
  public @NotNull String readString() {
    return new String(this.readByteArray(), StandardCharsets.UTF_8);
  }

  @Override
  public @NotNull DataBuf readDataBuf() {
    return this.hotRead(buf -> new NettyImmutableDataBuf(buf.readBytes(buf.readInt())));
  }

  @Override
  public byte[] toByteArray() {
    var bytes = new byte[this.readableBytes()];
    return this.hotRead(buf -> {
      buf.readBytes(bytes);
      return bytes;
    });
  }

  @Override
  public <T> @Nullable T readObject(@NotNull Class<T> type) {
    return DefaultObjectMapper.DEFAULT_MAPPER.readObject(this, type);
  }

  @Override
  public <T> T readObject(@NotNull Type type) {
    return DefaultObjectMapper.DEFAULT_MAPPER.readObject(this, type);
  }

  @Override
  public <T> @Nullable T readNullable(@NotNull Function<DataBuf, T> readerWhenNonNull) {
    return this.readNullable(readerWhenNonNull, null);
  }

  @Override
  public <T> T readNullable(@NotNull Function<DataBuf, T> readerWhenNonNull, T valueWhenNull) {
    return this.hotRead(buf -> {
      var isNonNull = buf.readBoolean();
      return isNonNull ? readerWhenNonNull.apply(this) : valueWhenNull;
    });
  }

  @Override
  public int readableBytes() {
    return this.byteBuf.readableBytes();
  }

  @Override
  public @NotNull DataBuf startTransaction() {
    this.byteBuf.markReaderIndex();
    this.byteBuf.markWriterIndex();

    return this;
  }

  @Override
  public @NotNull DataBuf redoTransaction() {
    this.byteBuf.resetReaderIndex();
    this.byteBuf.resetWriterIndex();

    return this;
  }

  @Override
  public @NotNull DataBuf.Mutable asMutable() {
    return new NettyMutableDataBuf(this.byteBuf);
  }

  @Override
  public @NotNull DataBuf disableReleasing() {
    this.releasable = false;
    return this;
  }

  @Override
  public @NotNull DataBuf enableReleasing() {
    this.releasable = true;
    return this;
  }

  @Override
  public void release() {
    if (this.releasable) {
      NettyUtils.safeRelease(this.byteBuf);
    }
  }

  @Override
  public void close() {
    this.release();
  }

  @Internal
  public @NotNull ByteBuf byteBuf() {
    return this.byteBuf;
  }

  protected @NotNull <T> T hotRead(@NotNull Function<ByteBuf, T> reader) {
    // get the result
    var result = reader.apply(this.byteBuf);
    // check if the reader index reached the end and try to release the message then
    if (!this.byteBuf.isReadable()) {
      this.release();
    }
    // return the read result
    return result;
  }
}
