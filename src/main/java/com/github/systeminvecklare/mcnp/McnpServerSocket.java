package com.github.systeminvecklare.mcnp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import com.github.systeminvecklare.mcnp.time.IClock;

public final class McnpServerSocket {
	private final ResourceHolder mainResourceHolder = new ResourceHolder();
	private final IClock localClock;
	private final DatagramSocket datagramSocket;
	private final IDatagramReceiver datagramReceiver;
	private final IAllocator allocator;
	private final ProtocolMessageSender messageSender;

	public McnpServerSocket(IClock localClock, int port) throws IOException {
		this.localClock = localClock;
		this.datagramSocket = new DatagramSocket(port);
		mainResourceHolder.addReleasable(ResourceHolder.createReleasable(datagramSocket));
		this.allocator = mainResourceHolder.addReleasable(new Allocator(UdpUtil.MAX_UDP_PAYLOAD*4));
		this.messageSender = new ProtocolMessageSender(allocator);
		this.datagramReceiver = new DatagramReceiver(datagramSocket, allocator);
	}
	
	
	public McnpSocket accept(IMcnpMessageListener mcnpMessageListener) throws InterruptedException {
		final McnpSocket socket = new McnpSocket();
		datagramReceiver.subscribeAccept(new IDatagramSubscriber() {
			@Override
			public void onDatagramPacket(McnpAddress sender, DatagramPacket packet) {
				synchronized (datagramReceiver) {
					TimesyncDatagramSubscriber timesyncDatagramSubscriber = new TimesyncDatagramSubscriber(socket, mcnpMessageListener);
					datagramReceiver.replaceSubscriber(sender, this, timesyncDatagramSubscriber);
					timesyncDatagramSubscriber.onDatagramPacket(sender, packet);
				}
			}
		});
		//TODO handle timeout
		try {
			synchronized (socket) {
				socket.wait();
			}
		} catch(InterruptedException e) {
			//TODO unsubscribe
			throw e;
		}
		return socket;
	}
	
	private class TimesyncDatagramSubscriber implements IDatagramSubscriber {
		private final McnpSocket socket;
		private final IMcnpMessageListener initialListener;

		public TimesyncDatagramSubscriber(McnpSocket socket, IMcnpMessageListener initialListener) {
			this.socket = socket;
			this.initialListener = initialListener;
		}
		
		@Override
		public void onDatagramPacket(McnpAddress sender, DatagramPacket packet) {
			IProtocolMessage message = ProtocolMessageMarshaller.parseMessage(packet);
			if(message instanceof TimeSyncRequest) {
				UUID uuid = ((TimeSyncRequest) message).getUuid();
				
				TimeSyncResponse response = new TimeSyncResponse(uuid, localClock.getTime());
				response.writeTo(packet);
				try {
					datagramSocket.send(packet);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if(message instanceof TimeSyncProposalRequest) {
				TimeSyncProposalRequest timeSyncProposalRequest = (TimeSyncProposalRequest) message;
				UUID acceptedProposal = timeSyncProposalRequest.getUuid();
				long offset = timeSyncProposalRequest.getUnifiedTime()-timeSyncProposalRequest.getYourTime();
				IClock unifiedClock = new OffsetClock(offset, localClock);
				
				ResourceHolder clientSocketResourceHolder = new ResourceHolder(mainResourceHolder);
				
				ClientSubscriber clientSubscriber = new ClientSubscriber(sender, datagramReceiver);
				clientSocketResourceHolder.addReleasable(clientSubscriber);
				
				TimeSyncResponserSubscriber timeSyncResponserSubscriber = new TimeSyncResponserSubscriber(acceptedProposal, sender);
				clientSubscriber.addListener(timeSyncResponserSubscriber);
				
				datagramReceiver.replaceSubscriber(sender, TimesyncDatagramSubscriber.this, clientSubscriber);
				
				socket.onConnected(sender, unifiedClock, acceptedProposal, clientSocketResourceHolder, clientSubscriber, messageSender.bind(datagramSocket, sender), initialListener);
				
				TimeSyncProposalResponse response = TimeSyncProposalResponse.accept(acceptedProposal);
				response.writeTo(packet);
				try {
					datagramSocket.send(packet);
				} catch (IOException e) {
					e.printStackTrace(); //TODO handle better
				}
				
				synchronized (socket) {
					socket.notifyAll();
				}
			}
		}
	}
	
	
	//DatagramReceiver manages a thread and keeps receiving packets as long there are subscribers.
	//If we get from a new address and noone is accepting new ones, ignore.
	//Callbacks to subscribers must not block as the are on the receiver thread.
	private interface IDatagramReceiver {
		void subscribeAccept(IDatagramSubscriber subscriber);//Receives the first unknown, and removes new-subscriber, and adds subsriber for that address 
		void subscribe(McnpAddress fromAddress, IDatagramSubscriber subscriber); //throw exception if multiple.
		void replaceSubscriber(McnpAddress fromAddress, IDatagramSubscriber oldSubscriber, IDatagramSubscriber newSubscriber);
		void unsubscribe(McnpAddress fromAddress, IDatagramSubscriber subscriber);
	}
	
	private static class DatagramReceiver implements IDatagramReceiver {
		private final Map<McnpAddress, IDatagramSubscriber> subscribers = new HashMap<McnpAddress, IDatagramSubscriber>();
		private final Queue<IDatagramSubscriber> acceptors = new LinkedList<IDatagramSubscriber>();
		
		private final AutoLooper thread; 
		private IAllocator.IBorrowedByteArray receiverPacketMemory = null;
		
		public DatagramReceiver(DatagramSocket serverSocket, IAllocator allocator) {
			AutoLooper.IRunCondition runCondition = new AutoLooper.IRunCondition() {
				@Override
				public boolean isMet() {
					synchronized (DatagramReceiver.this) {
						return !(subscribers.isEmpty() && acceptors.isEmpty());
					}
				}
			};
			
			this.thread = new AutoLooper(new Runnable() {
				@Override
				public void run() {
					ByteArray byteArray = receiverPacketMemory.getByteArray();
					DatagramPacket receiverPacket = byteArray.createDatagramPacket();
					try {
						receiverPacket.setLength(UdpUtil.MAX_UDP_PAYLOAD); //Reset length
						serverSocket.receive(receiverPacket);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					synchronized (DatagramReceiver.this) {
						McnpAddress sender = McnpAddress.from(receiverPacket);
						IDatagramSubscriber subscriber = subscribers.get(sender);
						if(subscriber == null) {
							if(!acceptors.isEmpty()) {
								subscriber = acceptors.poll();
								subscribers.put(sender, subscriber);
							}
						}
						if(subscriber != null) {
							subscriber.onDatagramPacket(sender, receiverPacket);
						}
					}
				}
			}, runCondition) {
				@Override
				protected void onBeforeStart() {
					if(receiverPacketMemory == null) {
						receiverPacketMemory = allocator.obtain(UdpUtil.MAX_UDP_PAYLOAD);
					}
				}
				
				@Override
				protected void onAfterStopped() {
					receiverPacketMemory.release();
					receiverPacketMemory = null;
				}
				@Override
				protected boolean onRuntimeException(RuntimeException e) {
					if(Thread.interrupted()) {
						return true;
					}
					if(e.getCause() instanceof IOException) {
						e.printStackTrace();
						return true;
					}
					return super.onRuntimeException(e);
				}
			};
		}
		
		@Override
		public synchronized void subscribeAccept(IDatagramSubscriber subscriber) {
			acceptors.add(subscriber);
			thread.checkCondition();
		}

		@Override
		public synchronized void subscribe(McnpAddress fromAddress, IDatagramSubscriber subscriber) {
			if(subscribers.containsKey(fromAddress)) {
				throw new IllegalArgumentException(fromAddress+" already listened to");
			}
			subscribers.put(fromAddress, subscriber);
			thread.checkCondition();
		}

		@Override
		public synchronized void replaceSubscriber(McnpAddress fromAddress, IDatagramSubscriber oldSubscriber, IDatagramSubscriber newSubscriber) {
			IDatagramSubscriber currentSubscriber = subscribers.get(fromAddress);
			if(currentSubscriber != oldSubscriber) {
				throw new IllegalArgumentException("Subscriber mismatch when replacing");
			}
			subscribers.put(fromAddress, newSubscriber);
			thread.checkCondition();
		}

		@Override
		public synchronized void unsubscribe(McnpAddress fromAddress, IDatagramSubscriber subscriber) {
			subscribers.remove(fromAddress);
			thread.checkCondition();
		}
	}
	
	private class ClientSubscriber implements IDatagramSubscriber, IReleasable, IProtocolMessageEventSource {
		private final McnpAddress clientAddress;
		private final IDatagramReceiver datagramReceiver;
		private volatile boolean released = false;
		private final ListenerList<IProtocolMessageListener> listeners = new ListenerList<>();
		
		public ClientSubscriber(McnpAddress clientAddress, IDatagramReceiver datagramReceiver) {
			this.clientAddress = clientAddress;
			this.datagramReceiver = datagramReceiver;
		}

		@Override
		public void onDatagramPacket(McnpAddress sender, DatagramPacket packet) {
//			//TODO 90% loss!
//			if(Math.random() < 0.9f) { //TODO would be nice if we could simulate packet loss on both client and server side! Very nice for testing.
//				return;
//			}
			if(!listeners.isEmpty()) {
				IProtocolMessage protocolMessage = ProtocolMessageMarshaller.parseMessage(packet);
				listeners.forEach(protocolMessage);
			}
		}


		@Override
		public void release() {
			released = true;
			listeners.clear();
			datagramReceiver.unsubscribe(clientAddress, this);
		}

		@Override
		public void addListener(IProtocolMessageListener listener) {
			if(!released) {
				listeners.addListener(listener);
			}
		}

		@Override
		public void removeListener(IProtocolMessageListener listener) {
			listeners.removeListener(listener);
		}

		@Override
		public void replaceListener(IProtocolMessageListener oldListener, IProtocolMessageListener newListener) {
			if(!released) {
				listeners.replaceListener(oldListener, newListener);
			} else {
				listeners.removeListener(oldListener);
			}
		}
	}
	
	private class TimeSyncResponserSubscriber implements IProtocolMessageListener {
		private final UUID acceptedProposal;
		private final McnpAddress clientAddress;
		
		public TimeSyncResponserSubscriber(UUID acceptedProposal, McnpAddress clientAddress) {
			this.acceptedProposal = acceptedProposal;
			this.clientAddress = clientAddress;
		}

		@Override
		public void onProtocolMessage(IProtocolMessage message) {
			if(message instanceof TimeSyncProposalRequest) {
				UUID uuid = ((TimeSyncProposalRequest) message).getUuid();
				try {
					if(acceptedProposal.equals(uuid)) {
						messageSender.send(datagramSocket, clientAddress, TimeSyncProposalResponse.accept(uuid));
					} else {
						messageSender.send(datagramSocket, clientAddress, TimeSyncProposalResponse.decline(uuid, "Already accepted other: "+acceptedProposal));
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
}
