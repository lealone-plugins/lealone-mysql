/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package com.lealone.plugins.mysql.server.protocol;

import java.nio.ByteBuffer;

import com.lealone.db.session.Session;
import com.lealone.net.NetBuffer;
import com.lealone.net.TransferOutputStream.GlobalWritableChannel;
import com.lealone.net.WritableChannel;

public class PacketOutput {

    private final GlobalWritableChannel channel;
    private final ByteBuffer buffer;

    public PacketOutput(WritableChannel writableChannel, NetBuffer outBuffer) {
        channel = new GlobalWritableChannel(writableChannel, outBuffer);
        buffer = channel.getByteBuffer();
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void allocate(int capacity) {
        channel.allocate(capacity);
    }

    public void flush() {
        channel.flush();
    }

    public void startWrite() {
        startWrite(Session.STATUS_OK);
    }

    public void startWrite(int status) {
        channel.startWrite(status);
    }

    public void writeOrFlush(byte[] src) {
        int offset = 0;
        int length = src.length;
        int remaining = buffer.remaining();
        while (length > 0) {
            if (remaining >= length) {
                buffer.put(src, offset, length);
                break;
            } else {
                buffer.put(src, offset, remaining);
                flush();
                startWrite();
                offset += remaining;
                length -= remaining;
                remaining = buffer.remaining();
                continue;
            }
        }
    }

    public void write(byte b) {
        buffer.put(b);
    }

    public void write(byte[] src) {
        buffer.put(src);
    }

    public void writeUB2(int i) {
        buffer.put((byte) (i & 0xff));
        buffer.put((byte) (i >>> 8));
    }

    public void writeUB3(int i) {
        buffer.put((byte) (i & 0xff));
        buffer.put((byte) (i >>> 8));
        buffer.put((byte) (i >>> 16));
    }

    public void writeInt(int i) {
        buffer.put((byte) (i & 0xff));
        buffer.put((byte) (i >>> 8));
        buffer.put((byte) (i >>> 16));
        buffer.put((byte) (i >>> 24));
    }

    public void writeFloat(float f) {
        writeInt(Float.floatToIntBits(f));
    }

    public void writeUB4(long l) {
        buffer.put((byte) (l & 0xff));
        buffer.put((byte) (l >>> 8));
        buffer.put((byte) (l >>> 16));
        buffer.put((byte) (l >>> 24));
    }

    public void writeLong(long l) {
        buffer.put((byte) (l & 0xff));
        buffer.put((byte) (l >>> 8));
        buffer.put((byte) (l >>> 16));
        buffer.put((byte) (l >>> 24));
        buffer.put((byte) (l >>> 32));
        buffer.put((byte) (l >>> 40));
        buffer.put((byte) (l >>> 48));
        buffer.put((byte) (l >>> 56));
    }

    public void writeDouble(double d) {
        writeLong(Double.doubleToLongBits(d));
    }

    public void writeLength(long l) {
        if (l < 251) {
            buffer.put((byte) l);
        } else if (l < 0x10000L) {
            buffer.put((byte) 252);
            writeUB2((int) l);
        } else if (l < 0x1000000L) {
            buffer.put((byte) 253);
            writeUB3((int) l);
        } else {
            buffer.put((byte) 254);
            writeLong(l);
        }
    }

    public void writeWithNull(byte[] src) {
        buffer.put(src);
        buffer.put((byte) 0);
    }

    public void writeWithLength(byte[] src) {
        int length = src.length;
        if (length < 251) {
            buffer.put((byte) length);
        } else if (length < 0x10000L) {
            buffer.put((byte) 252);
            writeUB2(length);
        } else if (length < 0x1000000L) {
            buffer.put((byte) 253);
            writeUB3(length);
        } else {
            buffer.put((byte) 254);
            writeLong(length);
        }
        buffer.put(src);
    }

    public void writeWithLength(byte[] src, byte nullValue) {
        if (src == null) {
            buffer.put(nullValue);
        } else {
            writeWithLength(src);
        }
    }
}
