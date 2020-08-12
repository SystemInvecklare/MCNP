package com.github.systeminvecklare.mcnp;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import com.github.systeminvecklare.mcnp.IAllocator.IBorrowedByteArray;

//TODO
//TODO The connected messages should all contain the following: 
//TODO 1. expiryTime (this helps the receiver immidiately skip stale messages) (this must be the same for all parts of multipart messages)
//TODO 2. isMultipart (a boolean/flag) this indicates if the UUID should be interpreted as uuid of message or uuid of multipart message
//TODO 3. acced (a boolean/flag) this indicates if the message should be acced (with acc-message)
//TODO 4. uuid (either of multipart or of individual short message. This helps receiver to only call back once per uuid. (and cache of send uuids can be kept to only have non-expired ones!))
//TODO (5. IF multipart, then partIndex and parts.)
//TODO 6. payload

/*package-protected*/ class ConnectedProtocolMessage extends BaseProtocolMessage implements IAccableProtocolMessage {
	public static final class MultiPartParams {
		/*package-protected*/ static final int SIZE = Short.BYTES*2;
		
		public final short partIndex;
		public final short parts;
		
		public MultiPartParams(int partIndex, int parts) {
			this.partIndex = (short) partIndex;
			this.parts = (short) parts;
		}
	}
	
	public static final int CONNECTED_SIZE = BASE_SIZE+Long.BYTES+1+Long.BYTES*2+Integer.BYTES;
	public static final int MAX_SHORT_PAYLOAD_SIZE = UdpUtil.MAX_UDP_PAYLOAD - CONNECTED_SIZE;
	public static final int MAX_MULTIPART_PART_PAYLOAD_SIZE = UdpUtil.MAX_UDP_PAYLOAD - CONNECTED_SIZE - MultiPartParams.SIZE;
	
	public static final byte FLAG_ACCED = 0b00000001;
	public static final byte FLAG_MULTIPART = 0b00000010;
	
	private final long expiryTime;
	private final byte flags;
	private final UUID uuid;
	private final MultiPartParams multiPartParams;
	private final ByteArray payload;
	
	public ConnectedProtocolMessage(long expiryTime, byte flags, UUID uuid, ByteArray payload) {
		this(expiryTime, flags, uuid, null, payload);
	}

	public ConnectedProtocolMessage(long expiryTime, byte flags, UUID uuid, MultiPartParams multiPartParams, ByteArray payload) {
		super(BaseProtocolMessage.TYPE_CONNECTED);
		this.expiryTime = expiryTime;
		this.flags = flags;
		this.uuid = uuid;
		this.multiPartParams = multiPartParams;
		int maxPayloadSize = isMultipart(flags) ? MAX_MULTIPART_PART_PAYLOAD_SIZE : MAX_SHORT_PAYLOAD_SIZE;
		if(payload.getLength() > maxPayloadSize) {
			throw new IllegalArgumentException("Payload must be less than or equal to "+maxPayloadSize);
		}
		if(isMultipart(flags) != (multiPartParams != null)) {
			throw new IllegalArgumentException(isMultipart(flags) ? "Missing multipart parameters" : "Got multipart parameters for short message");
		}
		this.payload = payload;
	}

	public long getExpiryTime() {
		return expiryTime;
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	@Override
	public boolean isAcced() {
		return isAcced(flags);
	}
	
	public boolean isMultipart() {
		return isMultipart(flags);
	}
	
	public int getPartIndex() {
		if(!isMultipart()) {
			throw new UnsupportedOperationException("getPartIndex() may only be called on multipart messages");
		}
		return Short.toUnsignedInt(multiPartParams.partIndex);
	}
	
	public int getParts() {
		if(!isMultipart()) {
			throw new UnsupportedOperationException("getParts() may only be called on multipart messages");
		}
		return Short.toUnsignedInt(multiPartParams.parts);
	}
	
	public ByteArray getPayload() {
		return payload;
	}
	
	@Override
	public long getChecksum(IAllocator allocator) {
		IBorrowedByteArray borrowedByteArray = allocator.obtain(getSize());
		try {
			ByteArray byteArray = borrowedByteArray.getByteArray();
			this.writeTo(byteArray.createByteBuffer());
			Checksum checksum = new Adler32();
			//TODO Yes, very cool with check sum and stuff. Really. But maybe we should just wait for an acc on the UUID?
			//     That would be safer as the chance of collision is 1/9223372036854775808 that of when using long.
			//     Also, we wouldn't have to write the whole message and calculate checksum.
			//     Also, we wouldn't need an allocator to calculate checksum.
			//     Also, it aligns more with the rest of the protocol.  
			byteArray.updateChecksum(checksum);
			return checksum.getValue();
		} finally {
			borrowedByteArray.release();
		}
	}
	
	private int getSize() { //TODO this should be added to all protocol messages
		return CONNECTED_SIZE+(isMultipart() ? MultiPartParams.SIZE : 0)+payload.getLength();
	}
	
	
	@Override
	protected void writeTo(ByteBuffer buffer) {
		super.writeTo(buffer);
		buffer.putLong(expiryTime);
		buffer.put(flags);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		if(isMultipart()) {
			buffer.putShort(multiPartParams.partIndex);
			buffer.putShort(multiPartParams.parts);
		}
		buffer.putInt(payload.getLength()); //TODO Need to check if max size will always be fitted in a short or char. if so use that instead of int.
		payload.putIn(buffer);
	}
	
	/*package-protected*/ static boolean isMultipart(byte flags) {
		return (flags & FLAG_MULTIPART) != 0;
	}
	
	private static boolean isAcced(byte flags) {
		return (flags & FLAG_ACCED) != 0;
	}
}
