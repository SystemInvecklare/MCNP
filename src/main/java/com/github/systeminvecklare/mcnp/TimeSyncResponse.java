package com.github.systeminvecklare.mcnp;

import java.nio.ByteBuffer;
import java.util.UUID;

/*package-protected*/ class TimeSyncResponse extends BaseProtocolMessage {
	public static final int TIMESYNC_RESPONSE_SIZE = BaseProtocolMessage.BASE_SIZE+Long.BYTES*3;
	private final UUID uuid;
	private final long localTime;

	public TimeSyncResponse(UUID uuid, long localTime) {
		super(TYPE_TIMESYNC_RESPONSE);
		this.uuid = uuid;
		this.localTime = localTime;
	}
	
	@Override
	protected void writeTo(ByteBuffer buffer) {
		super.writeTo(buffer);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		buffer.putLong(localTime);
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public long getLocalTime() {
		return localTime;
	}
}
