package com.github.systeminvecklare.mcnp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import com.github.systeminvecklare.mcnp.IAllocator.IBorrowedByteArray;

/*package-protected*/ class ProtocolMessageSender {
	private final IAllocator allocator;

	public ProtocolMessageSender(IAllocator allocator) {
		this.allocator = allocator;
	}
	
	public void send(DatagramSocket sender, McnpAddress receiver, IProtocolMessage protocolMessage) throws IOException {
		//TODO we could possibly have a 'getSize' on the protocol message. That would make a lot of sense....
		int size = guessMaxSize(protocolMessage);
		IBorrowedByteArray borrowedByteArray = allocator.obtain(size);
		try {
			DatagramPacket datagramPacket = borrowedByteArray.getByteArray().createDatagramPacket();
			receiver.stamp(datagramPacket);
			protocolMessage.writeTo(datagramPacket);
			sender.send(datagramPacket);
		} finally {
			borrowedByteArray.release();
		}
	}

	private int guessMaxSize(IProtocolMessage protocolMessage) {
		if(protocolMessage instanceof TimeSyncRequest) {
			return TimeSyncRequest.TIMESYNC_REQUEST_SIZE;
		} else if(protocolMessage instanceof TimeSyncResponse) {
			return TimeSyncResponse.TIMESYNC_RESPONSE_SIZE;
		} else if(protocolMessage instanceof TimeSyncProposalRequest) {
			return TimeSyncProposalRequest.TIMESYNC_PROPOSAL_REQUEST_SIZE;
		} else if(protocolMessage instanceof TimeSyncProposalResponse) {
			return TimeSyncProposalResponse.TIMESYNC_PROPOSAL_RESPONSE_MAXSIZE;
		} else {
			return UdpUtil.MAX_UDP_PAYLOAD;
		}
	}
	
	public IBoundProtocolMessageSender bind(DatagramSocket sender, McnpAddress receiver) {
		return new IBoundProtocolMessageSender() {
			@Override
			public void send(IProtocolMessage protocolMessage) throws IOException {
				ProtocolMessageSender.this.send(sender, receiver, protocolMessage);
			}
		};
	}
	
	public interface IBoundProtocolMessageSender {
		void send(IProtocolMessage protocolMessage) throws IOException;
	}
}
