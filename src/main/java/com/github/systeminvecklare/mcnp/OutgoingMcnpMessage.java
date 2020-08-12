package com.github.systeminvecklare.mcnp;

import java.util.UUID;

/*package-protected*/ class OutgoingMcnpMessage {
	public final long expiryTime;
	public final UUID protocolMessageUUID;
	public final McnpMessage mcnpMessage;
	
	public OutgoingMcnpMessage(long expiryTime, UUID protocolMessageUUID, McnpMessage mcnpMessage) {
		this.expiryTime = expiryTime;
		this.protocolMessageUUID = protocolMessageUUID;
		this.mcnpMessage = mcnpMessage;
	}
}
