package com.github.systeminvecklare.mcnp;

import java.nio.ByteBuffer;
import java.util.UUID;

/*package-protected*/ class TimeSyncProposalRequest extends BaseProtocolMessage {
	public static final int TIMESYNC_PROPOSAL_REQUEST_SIZE = BaseProtocolMessage.BASE_SIZE+Long.BYTES*4;
	private final UUID uuid;
	private final long unifiedTime;
	private final long yourTime;

	/**
	 * 
	 * @param uuid
	 * @param unifiedTime timestamp of proposed unified time
	 * @param yourTime receiver time at unified timestamp
	 */
	public TimeSyncProposalRequest(UUID uuid, long unifiedTime, long yourTime) {
		super(TYPE_TIMESYNC_PROPOSAL_REQUEST);
		this.uuid = uuid;
		this.unifiedTime = unifiedTime;
		this.yourTime = yourTime;
	}
	
	@Override
	protected void writeTo(ByteBuffer buffer) {
		super.writeTo(buffer);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		buffer.putLong(unifiedTime);
		buffer.putLong(yourTime);
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public long getUnifiedTime() {
		return unifiedTime;
	}
	
	public long getYourTime() {
		return yourTime;
	}
}
