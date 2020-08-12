package com.github.systeminvecklare.mcnp;

// TODO Remember: This is the type of message that Mcnp library users interact with. So they should not 
//                need to know any specifics.
// When THEY receive a message (in their listener) the library have already checked that it is not stale and 
// the library has also joined any multipart messages to one message 
public final class McnpMessage {
	private final ByteArray data;
	
	public McnpMessage(byte[] data) {
		this(new ByteArray(data));
	}
	
	public McnpMessage(ByteArray data) {
		this.data = data;
	}
	
	public ByteArray getData() {
		return data;
	}
}
