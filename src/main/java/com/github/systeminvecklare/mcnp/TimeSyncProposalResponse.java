package com.github.systeminvecklare.mcnp;

import java.nio.ByteBuffer;
import java.util.UUID;

/*package-protected*/ class TimeSyncProposalResponse extends BaseProtocolMessage {
	public static final int TIMESYNC_PROPOSAL_RESPONSE_MAXSIZE = BaseProtocolMessage.BASE_SIZE+Long.BYTES*2+1+1+Character.BYTES*30;
	
	public static final byte STATUS_PROPOSAL_ACCEPTED = 0;
	public static final byte STATUS_PROPOSAL_DECLINED = 1;
	
	private final UUID uuid;
	private final byte status;
	private final String declineMessageMax30Chars;
	
	private TimeSyncProposalResponse(UUID uuid, byte status, String declineMessageMax30Chars) {
		super(TYPE_TIMESYNC_PROPOSAL_RESPONSE);
		this.uuid = uuid;
		this.status = status;
		if(declineMessageMax30Chars != null && declineMessageMax30Chars.toCharArray().length > 30) {
			throw new IllegalArgumentException("Decline message can be max 30 chars.");
		}
		if(status == STATUS_PROPOSAL_DECLINED && declineMessageMax30Chars == null) {
			throw new IllegalArgumentException("Missing message for decline");
		}
		this.declineMessageMax30Chars = declineMessageMax30Chars;
	}
	
	@Override
	protected void writeTo(ByteBuffer buffer) {
		super.writeTo(buffer);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		buffer.put(status);
		if(status == STATUS_PROPOSAL_DECLINED) {
			char[] decline = declineMessageMax30Chars.toCharArray();
			buffer.put((byte) decline.length);
			for(char c : decline) {
				buffer.putChar(c);
			}
		}
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public byte getStatus() {
		return status;
	}
	
	public String getDeclineMessageMax30Chars() {
		return declineMessageMax30Chars;
	}
	
	public static TimeSyncProposalResponse accept(UUID uuid) {
		return new TimeSyncProposalResponse(uuid, STATUS_PROPOSAL_ACCEPTED, null);
	}
	
	public static TimeSyncProposalResponse decline(UUID uuid, String declineMessageMax30Chars) {
		return new TimeSyncProposalResponse(uuid, STATUS_PROPOSAL_DECLINED, declineMessageMax30Chars);
	}
}
