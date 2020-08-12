package com.github.systeminvecklare.mcnp;

import java.net.DatagramPacket;

/**
 * Listens to Udp messages and converts them to Mcnp protocol messages
 * @author Mattias Selin
 *
 */
/*package-protected*/ class UdpToProtocolMessageConverter implements IDatagramSubscriber, IProtocolMessageEventSource {
	private final ListenerList<IProtocolMessageListener> listeners = new ListenerList<>();
	private final UdpReceiver udpReceiver;
	private volatile boolean attached = false;
	
	public UdpToProtocolMessageConverter(UdpReceiver udpReceiver) {
		this.udpReceiver = udpReceiver;
	}
	
	@Override
	public void onDatagramPacket(McnpAddress sender, DatagramPacket packet) {
		if(!listeners.isEmpty()) {
			IProtocolMessage protocolMessage = ProtocolMessageMarshaller.parseMessage(packet);
			listeners.forEach(protocolMessage);
		}
	}
	
	private void ensureAttached() {
		if(!attached) {
			udpReceiver.addListener(this);
			attached = true;
		}
	}
	
	@Override
	public void addListener(IProtocolMessageListener listener) {
		synchronized (UdpToProtocolMessageConverter.this) {
			listeners.addListener(listener);
			ensureAttached();
		}
	}
	
	@Override
	public void removeListener(IProtocolMessageListener listener) {
		synchronized (UdpToProtocolMessageConverter.this) {
			listeners.removeListener(listener);
			if(listeners.isEmpty()) {
				udpReceiver.removeListener(this);
				attached = false;
			}
		}
	}
	
	@Override
	public void replaceListener(IProtocolMessageListener oldListener, IProtocolMessageListener newListener) {
		synchronized (UdpToProtocolMessageConverter.this) {
			listeners.replaceListener(oldListener, newListener);
			ensureAttached();
		}
	}
}
