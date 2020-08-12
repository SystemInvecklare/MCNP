package com.github.systeminvecklare.mcnp;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.UUID;


/*package-protected*/ class ProtocolMessageMarshaller {
	
	public static IProtocolMessage parseMessage(DatagramPacket datagramPacket) {
		return parseMessage(ByteBuffer.wrap(datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength()));
	}
	
	public static IProtocolMessage parseMessage(ByteBuffer buffer) {
		byte type = buffer.get();
		if(type == BaseProtocolMessage.TYPE_ACC) {
			return parseAccProtocolMessage(buffer);
		} else if(type == BaseProtocolMessage.TYPE_TIMESYNC_REQUEST) {
			return parseTimesyncRequest(buffer);
		} else if(type == BaseProtocolMessage.TYPE_TIMESYNC_RESPONSE) {
			return parseTimesyncResponse(buffer);
		} else if(type == BaseProtocolMessage.TYPE_TIMESYNC_PROPOSAL_REQUEST) {
			return parseTimesyncProposalRequest(buffer);
		} else if(type == BaseProtocolMessage.TYPE_TIMESYNC_PROPOSAL_RESPONSE) {
			return parseTimesyncProposalResponse(buffer);
		} else if(type == BaseProtocolMessage.TYPE_CONNECTED) {
			return parseConnectedProtocolMessage(buffer);
		} else {
			throw new IllegalArgumentException("Unknown type "+type);
		}
	}
	
	private static AccProtocolMessage parseAccProtocolMessage(ByteBuffer buffer) {
		long checksum = buffer.getLong();
		return new AccProtocolMessage(checksum);
	}
	
	private static ConnectedProtocolMessage parseConnectedProtocolMessage(ByteBuffer buffer) {
		long expiryTime = buffer.getLong();
		byte flags = buffer.get();
		long mostSignBits = buffer.getLong();
		long leastSignBits = buffer.getLong();
		UUID uuid = new UUID(mostSignBits, leastSignBits);
		ConnectedProtocolMessage.MultiPartParams multiPartParams = null;
		if((ConnectedProtocolMessage.isMultipart(flags))) {
			int partIndex = Short.toUnsignedInt(buffer.getShort());
			int parts = Short.toUnsignedInt(buffer.getShort());
			multiPartParams = new ConnectedProtocolMessage.MultiPartParams(partIndex, parts);
		}
		int payloadSize = buffer.getInt();
//		//TODO we could send in an allocator to the parseMessage functions so that we could reuse memory. Later.
		ByteArray payload = new ByteArray(payloadSize);
		payload.getFrom(buffer);
		return new ConnectedProtocolMessage(expiryTime, flags, uuid, multiPartParams, payload);
	}

	private static TimeSyncProposalResponse parseTimesyncProposalResponse(ByteBuffer buffer) {
		long mostSignBits = buffer.getLong();
		long leastSignBits = buffer.getLong();
		UUID uuid = new UUID(mostSignBits, leastSignBits);
		byte status = buffer.get();
		if(status == TimeSyncProposalResponse.STATUS_PROPOSAL_ACCEPTED) {
			return TimeSyncProposalResponse.accept(uuid);
		} else if(status == TimeSyncProposalResponse.STATUS_PROPOSAL_DECLINED) {
			byte messageLength = buffer.get();
			if(messageLength > 30) {
				messageLength = 30;
			}
			char[] declineMessageMax30Chars = new char[messageLength];
			for(int i = 0; i < messageLength; ++i) {
				declineMessageMax30Chars[i] = buffer.getChar();
			}
			return TimeSyncProposalResponse.decline(uuid, new String(declineMessageMax30Chars));
		} else {
			throw new IllegalArgumentException("Unknown status "+status);
		}
	}

	private static TimeSyncProposalRequest parseTimesyncProposalRequest(ByteBuffer buffer) {
		long mostSignBits = buffer.getLong();
		long leastSignBits = buffer.getLong();
		long unifiedTime = buffer.getLong();
		long yourTime = buffer.getLong();
		return new TimeSyncProposalRequest(new UUID(mostSignBits, leastSignBits), unifiedTime, yourTime);
	}

	private static TimeSyncResponse parseTimesyncResponse(ByteBuffer buffer) {
		long mostSignBits = buffer.getLong();
		long leastSignBits = buffer.getLong();
		long localTime = buffer.getLong();
		return new TimeSyncResponse(new UUID(mostSignBits, leastSignBits), localTime);
	}

	private static TimeSyncRequest parseTimesyncRequest(ByteBuffer buffer) {
		long mostSignBits = buffer.getLong();
		long leastSignBits = buffer.getLong();
		long localTime = buffer.getLong();
		return new TimeSyncRequest(new UUID(mostSignBits, leastSignBits), localTime);
	}
}
