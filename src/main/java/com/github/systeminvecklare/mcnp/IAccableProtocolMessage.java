package com.github.systeminvecklare.mcnp;

/*package-protected*/ interface IAccableProtocolMessage {
	boolean isAcced();
	long getChecksum(IAllocator allocator);
}
