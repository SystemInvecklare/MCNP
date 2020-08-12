package com.github.systeminvecklare.mcnp;

import java.net.DatagramPacket;

/*package-protected*/ interface IDatagramSubscriber {
	void onDatagramPacket(McnpAddress sender, DatagramPacket packet);
	
	public final class OnDatagramPacketEvent implements IEvent<IDatagramSubscriber> {
		private final McnpAddress sender;
		private final DatagramPacket packet;
		
		public OnDatagramPacketEvent(McnpAddress sender, DatagramPacket packet) {
			this.sender = sender;
			this.packet = packet;
		}

		@Override
		public void fireFor(IDatagramSubscriber listener) {
			listener.onDatagramPacket(sender, packet);
		}
	}
}
