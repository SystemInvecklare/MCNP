package com.github.systeminvecklare.mcnp;

import java.net.DatagramPacket;

/*package-protected*/ interface IProtocolMessage extends IEvent<IProtocolMessageListener> {
	void writeTo(DatagramPacket packet);
}
