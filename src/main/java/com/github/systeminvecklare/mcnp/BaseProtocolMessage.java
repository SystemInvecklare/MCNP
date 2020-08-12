package com.github.systeminvecklare.mcnp;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

/*package-protected*/ class BaseProtocolMessage implements IProtocolMessage {
	public static final int BASE_SIZE = 1;
	
	
	public static final byte TYPE_ACC = 0;
	public static final byte TYPE_TIMESYNC_REQUEST = 1;
	public static final byte TYPE_TIMESYNC_RESPONSE = 2;
	public static final byte TYPE_TIMESYNC_PROPOSAL_REQUEST = 3;
	public static final byte TYPE_TIMESYNC_PROPOSAL_RESPONSE = 4;
	public static final byte TYPE_CONNECTED = 5;
	
	private final byte type;
	
	public BaseProtocolMessage(byte type) {
		this.type = type;
	}

	@Override
	public final void writeTo(DatagramPacket packet) {
		packet.setLength(packet.getData().length-packet.getOffset()); //Set max length
		ByteArray byteArray = new ByteArray(packet.getData(), packet.getOffset(), packet.getLength());
		ByteBuffer buffer = byteArray.createByteBuffer();
		int bufferStart = buffer.position();
		writeTo(buffer);
		packet.setLength(buffer.position()-bufferStart);
	}
	
	protected void writeTo(ByteBuffer buffer) {
		buffer.put(type);
	}
	
	@Override
	public final void fireFor(IProtocolMessageListener listener) {
		listener.onProtocolMessage(this);
	}
}
