package com.github.systeminvecklare.mcnp;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.zip.Checksum;

public final class ByteArray {
	private final byte[] data;
	private final int offset;
	private final int length;
	
	public ByteArray(int length) {
		this(new byte[length]);
	}
	
	public ByteArray(byte[] data) {
		this(data, 0, data.length);
	}
	
	public ByteArray(byte[] data, int offset, int length) {
		if(length+offset > data.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		this.data = data;
		this.offset = offset;
		this.length = length;
	}
	
	public ByteArrayInputStream createInputStream() {
		return new ByteArrayInputStream(data, offset, length);
	}
	
	public void copyTo(ByteArray byteArray) {
		if(byteArray.length < this.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		System.arraycopy(this.data, this.offset, byteArray.data, byteArray.offset, this.length);
	}
	
	public void copyFrom(ByteArray byteArray) {
		if(this.length < byteArray.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		System.arraycopy(byteArray.data, byteArray.offset, this.data, this.offset, byteArray.length);
	}
	
	public static void copy(ByteArray source, int sourcePos, ByteArray dest, int destPos, int length) {
		if(source.length < length || dest.length < length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		System.arraycopy(source.data, source.offset+sourcePos, dest.data, dest.offset+destPos, length);
	}
	
	public ByteBuffer createByteBuffer() {
		return ByteBuffer.wrap(data, offset, length);
	}
	
	public int getLength() {
		return length;
	}
	
	public ByteArray subArray(int offset, int length) {
		if(this.length < offset+length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		return new ByteArray(this.data, this.offset+offset, length);
	}

	public DatagramPacket createDatagramPacket() {
		return new DatagramPacket(data, offset, length);
	}

	public ByteArray copy() {
		ByteArray copy = new ByteArray(this.length);
		this.copyTo(copy);
		return copy;
	}

	public void putIn(ByteBuffer buffer) {
		buffer.put(data, offset, length);
	}

	public void getFrom(ByteBuffer buffer) {
		buffer.get(data, offset, length);
	}

	public String createString(Charset charset) {
		return new String(data, offset, length, charset);
	}

	public void updateChecksum(Checksum checksum) {
		checksum.update(data, offset, length);
	}
}
