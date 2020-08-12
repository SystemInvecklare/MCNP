package com.github.systeminvecklare.mcnp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/*package-protected*/ class UdpReceiver implements IReleasable {
	private final AutoLooper thread;
	private volatile DatagramPacket receivePacket;
	private volatile boolean released = false;
	private final ListenerList<IDatagramSubscriber> eventBroadcaster = new ListenerList<>();
	
	public UdpReceiver(DatagramSocket datagramSocket, DatagramPacket datagramPacket) {
		this.receivePacket = datagramPacket;
		AutoLooper.IRunCondition loopCondition = new AutoLooper.IRunCondition() {
			@Override
			public boolean isMet() {
				return !eventBroadcaster.isEmpty() && !released;
			}
		};
		this.thread = new AutoLooper(new Runnable() {
			@Override
			public void run() {
				DatagramPacket receivePacketSnapshot = receivePacket;
				if(receivePacketSnapshot == null) {
					throw new RuntimeException("Unexpected!");
				}
				receivePacketSnapshot.setLength(UdpUtil.MAX_UDP_PAYLOAD); //Reset length
				try {
					datagramSocket.receive(receivePacketSnapshot);
				} catch (IOException e) {
					if(!released) {
						throw new RuntimeException(e);
					}
				}
				
				eventBroadcaster.forEach(new IDatagramSubscriber.OnDatagramPacketEvent(McnpAddress.from(receivePacketSnapshot) ,receivePacketSnapshot));
			}
		}, loopCondition) {
			@Override
			protected void onBeforeStart() {
				if(released) {
					return;
				}
				if(receivePacket == null) {
					receivePacket = new DatagramPacket(new byte[UdpUtil.MAX_UDP_PAYLOAD], UdpUtil.MAX_UDP_PAYLOAD);
				}
			}
			
			@Override
			protected void onAfterStopped() {
				receivePacket = null;
			}
		};
	}
	
	public synchronized void addListener(IDatagramSubscriber listener) {
		synchronized (thread) {
			eventBroadcaster.addListener(listener);
			thread.checkCondition();
		}
	}
	
	public synchronized void removeListener(IDatagramSubscriber listener) {
		synchronized (thread) {
			eventBroadcaster.removeListener(listener);
			thread.checkCondition();
		}
	}
	
	public synchronized void replaceListener(IDatagramSubscriber oldListener, IDatagramSubscriber newListener) {
		synchronized (thread) {
			eventBroadcaster.replaceListener(oldListener, newListener);
			thread.checkCondition();
		}
	}
	
	@Override
	public void release() {
		released = true;
		eventBroadcaster.clear();
		thread.release();
	}
}
