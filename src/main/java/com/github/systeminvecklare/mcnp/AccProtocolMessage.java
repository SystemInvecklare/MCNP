package com.github.systeminvecklare.mcnp;

import java.nio.ByteBuffer;

/*package-protected*/ class AccProtocolMessage extends BaseProtocolMessage {
	public static final int ACC_MESSAGE_SIZE = BASE_SIZE+Long.BYTES;
	
	private final long checksum;

	public AccProtocolMessage(long checksum) {
		super(BaseProtocolMessage.TYPE_ACC);
		this.checksum = checksum;
	}

	public AccProtocolMessage(IAccableProtocolMessage accableProtocolMessage, IAllocator allocator) {
		this(accableProtocolMessage.getChecksum(allocator));
	}
	
	public long getAccChecksum() {
		return checksum;
	}

	@Override
	protected void writeTo(ByteBuffer buffer) {
		super.writeTo(buffer);
		buffer.putLong(checksum);
	}
}
