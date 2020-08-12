package com.github.systeminvecklare.mcnp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.github.systeminvecklare.mcnp.ConnectedProtocolMessage.MultiPartParams;
import com.github.systeminvecklare.mcnp.IAllocator.IBorrowedByteArray;
import com.github.systeminvecklare.mcnp.ProtocolMessageSender.IBoundProtocolMessageSender;
import com.github.systeminvecklare.mcnp.time.IClock;
import com.github.systeminvecklare.mcnp.time.SystemClock;

public final class McnpSocket implements Closeable {
	private final IClock localClock = new SystemClock();
	private McnpAddress connectedAddress = null;
	private IClock unifiedClock = null;

					//    But what we really should do is have the timesync-protocol in the networklayer.
					//    And then the messages can have a timestamp and we can just have:
					//    mcnp (mattes cool networking protocol) can just have: 
					//    McnpSocket clientSocket = new McnpSocket();
					//    clientSocket.setConnectTimeout(10000);
					//    clientSocket.connect(new McnpAddress("localHost", 4303)); //Blocks until timesync finishes or times out.
					//    clientSocket.send(new DefaultMessage("asdasdasd")); //No block
					//    boolean ok = clientSocket.sendAcced(...); //Blocks!
					//    clientSocket.addListener(new fsdf() {
					//        onMessage(IMessage message) {
					//            // only triggers on received messages is not stale. (messages have an expiration-date)
					//            // messages can also have an UUID (multipart share UUID). That way the receiver can
					//            // keep a cache of received UUIDs and just evict those who have expired!
					//        }
					//    });
					//  ----------------------
					//   McnpServerSocket serverSocket = new McnpServerSocket(4303);
					//   McnpSocket connected = serverSocket.accept(new messageListener() {...}); //Blocks until a connection is made. Handles one attempt at a time.
					//   // We have to send in a message-listener so that we don't miss any messages
					//
					//   Parts of the Mcnp:
					//   * Acc-messages
					//   * TimeSync
					//   * ExpirationTime
					//   * Multipart?
	
	private long minResponseWaitTime = 5;
	private long connectTimoutTime = 5000;
	private int sendBufferSize = UdpUtil.MAX_UDP_PAYLOAD*2; //Size of allocator used for buffering.
	private int mcnpMessageFreshTime = 3000; //Time until expiry for messages
	private int accTimeoutTime = 5000; //Timeout when sending acced messages
	private int accSendInitialInterval = 20; //Time before first resending of acced message
	private int accSendInterval = 5; //Time between resending acced message (after first resend)
	private int burstInterval = 5; //Time between messages when bursting
	private ResourceHolder resourceHolder = null;
	private ProtocolMessageSender.IBoundProtocolMessageSender messageSender = null;
	private IAllocator accAllocator = null;
	private IProtocolMessageEventSource protocolMessageEventSource = null;
	private final IncommingMessageHandler incommingMessageHandler = new IncommingMessageHandler();
	
	public McnpSocket() {
	}
	
	/*
	 * Blocks until a connection can be made.
	 */
	public void connect(McnpAddress address) throws IOException, TimeoutException {
		connect(this, address);
	}
	
	private static void connect(McnpSocket socket, McnpAddress address) throws IOException, TimeoutException {
		ResourceHolder resourceHolder = new ResourceHolder();
		try {
			long connectionStart = System.currentTimeMillis();
			DatagramSocket datagramSocket = new DatagramSocket();
			resourceHolder.addReleasable(ResourceHolder.createReleasable(datagramSocket), 1);
			
			IAllocator sendBufferAllocator = new Allocator(socket.sendBufferSize);
			ProtocolMessageSender.IBoundProtocolMessageSender messageSender = new ProtocolMessageSender(sendBufferAllocator).bind(datagramSocket, address);
			
			UdpToProtocolMessageConverter messageConverter;
			{
				UdpReceiver udpReceiver = new UdpReceiver(datagramSocket, null);
				resourceHolder.addReleasable(udpReceiver);
				messageConverter = new UdpToProtocolMessageConverter(udpReceiver);
			}
			Collection<TimeSyncMeasurement> measurements = new ArrayList<McnpSocket.TimeSyncMeasurement>();
			//TODO We should have one number that describes the number of messages we need back, and an other that describes specifies how often we try to resend.
			int syncMessages = 20; //TODO Make amount configurable (but make sure at least 2)
			for(int i = 0; i < syncMessages; ++i) {
				measurements.add(new TimeSyncMeasurement(UUID.randomUUID()));
			}
			
			TimeSyncCollector timeSyncCollector = new TimeSyncCollector(socket.localClock, measurements);
			messageConverter.addListener(timeSyncCollector);
			try {
				//TODO do a loop and retry. If we try to connect at the same time as a different client all initial messages might be missed!
				for(TimeSyncMeasurement measurement : measurements) {
					long sendTime = socket.localClock.getTime();
					TimeSyncRequest timeSyncRequest = new TimeSyncRequest(measurement.uuid, sendTime);
					messageSender.send(timeSyncRequest);
					measurement.onSent(sendTime);
				}
				long timeoutTimestamp = socket.connectTimoutTime+connectionStart;
				
				int completedMeasurements = 0;
				while(completedMeasurements < syncMessages/2 && System.currentTimeMillis() < timeoutTimestamp) {
					completedMeasurements = 0;
					for(TimeSyncMeasurement measurement : measurements) {
						if(measurement.isComplete()) {
							completedMeasurements++;
						}
					}
				}
			} finally {
				messageConverter.removeListener(timeSyncCollector);
			}
			
			
			//offset mean remoteClock = myClock+offset;
			Long minRemoteTimeOffset = null;
			Long maxRemoteTimeOffset = null;
			for(TimeSyncMeasurement measurement : measurements) {
				if(measurement.isComplete()) {
					long roundTrip = measurement.responseTimeInLocal-measurement.sendTimeInLocal;
					long measuredMinRemoteTimeOffset = measurement.remoteTime-measurement.responseTimeInLocal;
					long measuredMaxRemoteTimeOffset = measurement.remoteTime+roundTrip-measurement.responseTimeInLocal;
					//We want to shrink the interval!
					if(minRemoteTimeOffset == null || measuredMinRemoteTimeOffset > minRemoteTimeOffset) {
						minRemoteTimeOffset = measuredMinRemoteTimeOffset;
					}
					if(maxRemoteTimeOffset == null || measuredMaxRemoteTimeOffset < maxRemoteTimeOffset) {
						maxRemoteTimeOffset = measuredMaxRemoteTimeOffset;
					}
				}
			}
			
			if(maxRemoteTimeOffset != null) {
				long offsetEstimation = (maxRemoteTimeOffset+minRemoteTimeOffset)/2;
				System.out.println("Guessed offset "+offsetEstimation);
				long unifiedTime = socket.localClock.getTime(); //Propose localClock as unifiedTime
				final UUID proposalUUID = UUID.randomUUID();
				TimeSyncProposalRequest timeSyncProposalRequest = new TimeSyncProposalRequest(proposalUUID, unifiedTime, unifiedTime+offsetEstimation);
				
				TimeSyncProposalResponseListener proposalResponseListener 
				= new TimeSyncProposalResponseListener(proposalUUID);
				messageConverter.addListener(proposalResponseListener);
				
				byte responseStatus;
				try {
					long usedUpConnectionTime = System.currentTimeMillis()-connectionStart;
					long timeout = socket.connectTimoutTime-usedUpConnectionTime;
					if(timeout <= 0) {
						throw new TimeoutException();
					}
					IBorrowedByteArray borrowedByteArray = sendBufferAllocator.obtain(TimeSyncProposalRequest.TIMESYNC_PROPOSAL_REQUEST_SIZE);
					try {
						DatagramPacket packet = borrowedByteArray.getByteArray().createDatagramPacket();
						responseStatus = socket.sendProposal(timeSyncProposalRequest, proposalResponseListener, datagramSocket, address, packet, timeout);
					} finally {
						borrowedByteArray.release();
					}
				} catch (InterruptedException e) {
					//TODO we should handle this for timeouts
					throw new IOException(e); 
				}
				if(responseStatus == TimeSyncProposalResponse.STATUS_PROPOSAL_ACCEPTED) {
					//Got accepted!
					messageConverter.removeListener(proposalResponseListener);
					//TODO could use same allocator in whole socket. (if so, send here.)
					socket.onConnected(
							address, 
							socket.localClock, 
							proposalUUID, 
							resourceHolder, 
							messageConverter, 
							messageSender,
							null);
				} else {
					//TODO sendProposal should return the response instead! So we can have better messages
					throw new IOException("Time sync proposal not accpeted (status="+responseStatus+")");
				}
			} else {
				throw new IOException("No responses to time syncs!");
			}
		} catch (IOException | TimeoutException | RuntimeException e) {
			resourceHolder.release();
			throw e;
		}
		
		
		
		
		//1. send x TIMESYNC_REQUEST (with different UUIDs)
		//2. get TIMESYNC_RESPONSE (with same UUID) see how many return within accepted time.
		//3. we get a time diff estimate based on the ones that returned. Pick the best one
		//4. When we are satisfied we send a TIMESYNC_AGREEMENT_REQUEST (with UUID) with purposed time sync. This message is to be acced
		//5. get a TIMESYNC_AGREEMENT_RESPONSE (same UUID) indicating if accepted or not) this message is to be acced.
		//6. done.
	}
	
	/*package-protected*/ final synchronized void onConnected(McnpAddress connectedAddress, IClock unifiedClock, UUID acceptedProposal, ResourceHolder resourceHolder, IProtocolMessageEventSource protocolMessageEventSource, ProtocolMessageSender.IBoundProtocolMessageSender messageSender, IMcnpMessageListener initialListener) {
		this.connectedAddress = connectedAddress;
		this.unifiedClock = unifiedClock;
		this.resourceHolder = resourceHolder;
		this.messageSender = messageSender;
		this.protocolMessageEventSource = protocolMessageEventSource;
		
		if(initialListener != null) {
			//TODO Actually, since we want to be able to add listeners before socket is connected, we should skip sending the whole thingy here.
			incommingMessageHandler.addListener(initialListener);
		}
		
		this.accAllocator = resourceHolder.addReleasable(new Allocator(AccProtocolMessage.ACC_MESSAGE_SIZE*10));
		resourceHolder.addReleasable(incommingMessageHandler);
		incommingMessageHandler.onSocketConnected(protocolMessageEventSource, unifiedClock, messageSender, accAllocator);
	}
	
	private byte sendProposal(TimeSyncProposalRequest timeSyncProposalRequest, TimeSyncProposalResponseListener proposalResponseListener, DatagramSocket datagramSocket, McnpAddress address, DatagramPacket packet, long sendProposalTimeout) throws IOException, InterruptedException, TimeoutException {
		long start = System.currentTimeMillis();
		while(System.currentTimeMillis()-start < sendProposalTimeout) {
			address.stamp(packet);
			timeSyncProposalRequest.writeTo(packet);
			datagramSocket.send(packet);
			
			Byte responseStatus = proposalResponseListener.getStatus();
			if(responseStatus != null) {
				//TODO return the response instead!
				return responseStatus.byteValue();
			}
			Thread.sleep(minResponseWaitTime);
			responseStatus = proposalResponseListener.getStatus();
			if(responseStatus != null) {
				//TODO return the response instead!
				return responseStatus.byteValue();
			}
		}
		throw new TimeoutException("Time sync proposal timed out");
	}
	
	public void addListener(IMcnpMessageListener listener) {
		incommingMessageHandler.addListener(listener);
	}
	
	public void removeListener(IMcnpMessageListener listener) {
		incommingMessageHandler.removeListener(listener);
	}
	
	public McnpAddress getConnectedAddress() {
		return connectedAddress;
	}
	
	public IClock getUnifiedClock() {
		return unifiedClock;
	}
	
	@Override
	public void close() throws IOException {
		//TODO set some state-flags also
		resourceHolder.release();
	}
	
	public void send(McnpMessage message) throws IOException {
		//TODO check state (check connected)
		
		sendBurst(message, 1);
	}
	
	public void sendBurst(McnpMessage message, int copies) throws IOException {
		//TODO check state (check connected)
		
		long expiryTime = unifiedClock.getTime()+mcnpMessageFreshTime+burstInterval*(copies-1);
		
		UUID uuid = UUID.randomUUID();
		ByteArray data = message.getData();
		for(int burstCopy = 0; burstCopy < copies; ++burstCopy) {
			if(burstCopy != 0) {
				try {
					Thread.sleep(burstInterval);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			if(data.getLength() > ConnectedProtocolMessage.MAX_SHORT_PAYLOAD_SIZE) {
				for(ConnectedProtocolMessage multipartMessage : createMultipart(expiryTime, uuid, false, data, new ArrayList<>())) {
					messageSender.send(multipartMessage);
				}
			} else {
				byte flags = 0;
				messageSender.send(new ConnectedProtocolMessage(expiryTime, flags, uuid, data));
			}
		}
	}
	
	private static List<ConnectedProtocolMessage> createMultipart(long expiryTime, UUID uuid, boolean acced, ByteArray data, List<ConnectedProtocolMessage> result) {
		byte flags = ConnectedProtocolMessage.FLAG_MULTIPART;
		if(acced) {
			flags |= ConnectedProtocolMessage.FLAG_ACCED;
		}
		final int MAX_PART_SIZE = ConnectedProtocolMessage.MAX_MULTIPART_PART_PAYLOAD_SIZE;
		int remaining = data.getLength();
		int parts = remaining/MAX_PART_SIZE;
		if(remaining%MAX_PART_SIZE > 0) {
			parts++;
		}
		int partIndex = 0;
		while(remaining > 0) {
			int partLength = Math.min(remaining, MAX_PART_SIZE);
			int offset = data.getLength()-remaining;
			MultiPartParams multipartParams = new MultiPartParams(partIndex, parts);
			result.add(new ConnectedProtocolMessage(expiryTime, flags, uuid, multipartParams, data.subArray(offset, partLength)));
			remaining -= partLength;
			partIndex++;
		}
		return result;
	}
	
	public boolean sendAcced(McnpMessage message) throws IOException {
		//TODO check state (check connected)
		
		//TODO we should add a accBurstParameter so that initial send of acced messages are bursted
		
		long expiryTime = unifiedClock.getTime()+accTimeoutTime;
		
		UUID uuid = UUID.randomUUID();
		ByteArray data = message.getData();
		if(data.getLength() > ConnectedProtocolMessage.MAX_SHORT_PAYLOAD_SIZE) {
			List<ConnectedProtocolMessage> messages = createMultipart(expiryTime, uuid, true, data, new ArrayList<>());
			return sendAccedInternal(expiryTime, messages);
		} else {
			
			byte flags = ConnectedProtocolMessage.FLAG_ACCED;
			ConnectedProtocolMessage protocolMessage = new ConnectedProtocolMessage(expiryTime, flags, uuid, data);
			
			return sendAccedInternal(expiryTime, Collections.singleton(protocolMessage));
		}
	}
	
	
	private boolean sendAccedInternal(long expiryTime, Collection<ConnectedProtocolMessage> messages) throws IOException {
		Map<ConnectedProtocolMessage, Long> checksums = new HashMap<>();
		for(ConnectedProtocolMessage part : messages) {
			checksums.put(part, part.getChecksum(accAllocator));
		}
		AccListener accListener = new AccListener(checksums.values());
		protocolMessageEventSource.addListener(accListener);
		try {
			boolean firstWait = true;
			while(unifiedClock.getTime() < expiryTime) {
				boolean allDone = true;
				for(Entry<ConnectedProtocolMessage, Long> entry : checksums.entrySet()) {
					if(!accListener.isAcced(entry.getValue())) {
						messageSender.send(entry.getKey());
//						if(entry.getKey().isMultipart()) {
//							System.out.println("Sending part "+entry.getKey().getPartIndex());
//						}
						allDone = false;
					}
				}
				if(allDone) {
					return true;
				}
				try {
					Thread.sleep(firstWait ? accSendInitialInterval : accSendInterval);
					firstWait = false;
				} catch(InterruptedException e) {
					return false;
				}
			}
			return false;
		} finally {
			protocolMessageEventSource.removeListener(accListener);
		}
	}
	
	private static class TimeSyncMeasurement {
		private final UUID uuid;
		private volatile Long sendTimeInLocal = null;
		private volatile Long responseTimeInLocal = null;
		private volatile Long remoteTime = null;

		public TimeSyncMeasurement(UUID uuid) {
			this.uuid = uuid;
		}

		public boolean isComplete() {
			return sendTimeInLocal != null && responseTimeInLocal != null && remoteTime != null;
		}

		public void onSent(long sendTime) {
			this.sendTimeInLocal = sendTime;
		}

		public void onResponse(long responseTimeInLocal, long remoteTime) {
			this.responseTimeInLocal = responseTimeInLocal;
			this.remoteTime = remoteTime;
		}
	}
	
	private static class TimeSyncCollector implements IProtocolMessageListener {
		private final IClock localClock;
		private final Map<UUID, TimeSyncMeasurement> measurements = new HashMap<UUID, McnpSocket.TimeSyncMeasurement>();
		
		public TimeSyncCollector(IClock localClock, Collection<TimeSyncMeasurement> measurements) {
			this.localClock = localClock;
			for(TimeSyncMeasurement measurement : measurements) {
				this.measurements.put(measurement.uuid, measurement);
			}
		}
		
		@Override
		public void onProtocolMessage(IProtocolMessage message) {
			if(message instanceof TimeSyncResponse) {
				TimeSyncResponse response = (TimeSyncResponse) message;
				TimeSyncMeasurement measurement = measurements.remove(response.getUuid());
				if(measurement != null) {
					long remoteTime = response.getLocalTime();
					measurement.onResponse(localClock.getTime(), remoteTime);
				}
			}
		}
	}
	
	private static class TimeSyncProposalResponseListener implements IProtocolMessageListener {
		private final UUID proposalUUID;
		private Byte acceptanceStatus = null;
		
		public TimeSyncProposalResponseListener(UUID proposalUUID) {
			this.proposalUUID = proposalUUID;
		}
		
		@Override
		public void onProtocolMessage(IProtocolMessage message) {
			if(message instanceof TimeSyncProposalResponse) {
				TimeSyncProposalResponse proposalResponse = (TimeSyncProposalResponse) message;
				if(proposalUUID.equals(proposalResponse.getUuid())) {
					byte status = proposalResponse.getStatus();
					synchronized (TimeSyncProposalResponseListener.this) {
						acceptanceStatus = status;
					}
				}
			}
		}
		
		public synchronized Byte getStatus() {
			return acceptanceStatus;
		}
	}
	
	private static class IncommingMessageHandler implements IProtocolMessageListener, IReleasable {
		private volatile boolean released = false;
		private volatile boolean listening = false;
		private IProtocolMessageEventSource protocolMessageEventSource = null;
		private IClock unifiedClock;
		private IBoundProtocolMessageSender messageSender;
		private IAllocator accAllocator;
		private UuidCache completedUuidCache;
		private MultipartHandler multipartHandler;
		private final ListenerList<IMcnpMessageListener> mcnpMessageListeners = new ListenerList<>(); 
		private final ResourceHolder resourceHolder = new ResourceHolder();
		private final MessageQueue<ConnectedProtocolMessage> messageQueue = resourceHolder.addReleasable(new MessageQueue<>());
		private final List<ConnectedProtocolMessage> harvestedMessages = new ArrayList<>();
		//TODO replace with AutoLooper
		private final Looper looper = resourceHolder.addReleasable(new Looper(new Runnable() {
			@Override
			public void run() {
				harvestedMessages.clear();
				messageQueue.harvest(harvestedMessages);
				
				//TODO reuse?
				List<OutgoingMcnpMessage> outgoingMcnpMessages = new ArrayList<>(); 
				for(ConnectedProtocolMessage protocolMessage : harvestedMessages) {
					if(protocolMessage.isMultipart()) {
						synchronized (IncommingMessageHandler.this) {
							multipartHandler.supply(protocolMessage, outgoingMcnpMessages);
						}
					} else {
						UUID messageUUID = protocolMessage.getUuid(); 
						
						ByteArray data = protocolMessage.getPayload();
						byte[] mcnpData = new byte[data.getLength()];
						data.copyTo(new ByteArray(mcnpData));
						McnpMessage mcnpMessage = new McnpMessage(mcnpData);
						synchronized (IncommingMessageHandler.this) {
							if(!completedUuidCache.hasUUID(messageUUID)) {
								outgoingMcnpMessages.add(new OutgoingMcnpMessage(protocolMessage.getExpiryTime(), messageUUID, mcnpMessage));
							}
						}
					}
				}
				harvestedMessages.clear();
				synchronized (IncommingMessageHandler.this) {
					Iterator<OutgoingMcnpMessage> iterator = outgoingMcnpMessages.iterator();
					while(iterator.hasNext()) {
						OutgoingMcnpMessage outgoingMcnpMessage = iterator.next();
						if(completedUuidCache.hasUUID(outgoingMcnpMessage.protocolMessageUUID)) {
							iterator.remove();
						} else {
							completedUuidCache.addUuid(outgoingMcnpMessage.expiryTime, outgoingMcnpMessage.protocolMessageUUID);
						}
					}
					completedUuidCache.prune();
					multipartHandler.prune();
				}
				
				Iterator<OutgoingMcnpMessage> iterator = outgoingMcnpMessages.iterator();
				while (iterator.hasNext()) {
					mcnpMessageListeners.forEach(new OnMcnpMessageEvent(iterator.next().mcnpMessage));
					iterator.remove();
				}
			}
		}) {
			@Override
			public void onAfterStopped() {
				harvestedMessages.clear();
			}
		});
		
		@Override
		public void onProtocolMessage(IProtocolMessage protocolMessage) {
			synchronized (this) {
				if(!mcnpMessageListeners.isEmpty() && !released) {
					if(protocolMessage instanceof ConnectedProtocolMessage) {
						ConnectedProtocolMessage connectedProtocolMessage = (ConnectedProtocolMessage) protocolMessage;
						if(unifiedClock.getTime() <= connectedProtocolMessage.getExpiryTime()) {
							if(connectedProtocolMessage.isAcced()) {
								try {
									messageSender.send(new AccProtocolMessage(connectedProtocolMessage, accAllocator));
								} catch (IOException e) {
									e.printStackTrace();
									//TODO handle better
								}
							}
							
							if(!completedUuidCache.hasUUID(connectedProtocolMessage.getUuid())) {
								//TODO We should verify that the UUID is not already queued in the messagequeue...
								messageQueue.queueMessage(connectedProtocolMessage);
							}
						}
					}
				}
			}
		}
		
		public synchronized void onSocketConnected(IProtocolMessageEventSource protocolMessageEventSource, IClock unifiedClock, IBoundProtocolMessageSender messageSender, IAllocator accAllocator) {
			this.protocolMessageEventSource = protocolMessageEventSource;
			this.unifiedClock = unifiedClock;
			this.completedUuidCache = resourceHolder.addReleasable(new UuidCache(unifiedClock));
			this.messageSender = messageSender;
			this.accAllocator = accAllocator;
			//TODO get sizes for allocators from socket settings instead.
			IAllocator multipartWorkingMemory = resourceHolder.addReleasable(new Allocator(UdpUtil.MAX_UDP_PAYLOAD*100));
			this.multipartHandler = resourceHolder.addReleasable(new MultipartHandler(multipartWorkingMemory, unifiedClock, completedUuidCache));
			maybeStart();
		}
		
		private synchronized void maybeStart() {
			if(released || protocolMessageEventSource == null) {
				return;
			}
			if(!mcnpMessageListeners.isEmpty()) {
				if(!listening) {
					protocolMessageEventSource.addListener(this);
					listening = true;
				}
				looper.start();
			}
		}
		
		private synchronized void maybeStop() {
			if(released) {
				return;
			}
			if(mcnpMessageListeners.isEmpty()) {
				if(protocolMessageEventSource != null) {
					protocolMessageEventSource.removeListener(this);
				}
				listening = false;
				looper.stop();
			}
		}

		public synchronized void addListener(IMcnpMessageListener listener) {
			mcnpMessageListeners.addListener(listener);
			maybeStart();
		}
		
		public synchronized void removeListener(IMcnpMessageListener listener) {
			mcnpMessageListeners.removeListener(listener);
			maybeStop();
		}

		@Override
		public synchronized void release() {
			released = true;
			protocolMessageEventSource.removeListener(this);
			resourceHolder.release();
		}
	}
	
	private static class AccListener implements IProtocolMessageListener {
		private final Set<Long> checksums;
		
		public AccListener(Collection<Long> checksums) {
			this.checksums = new HashSet<>(checksums);
		}

		@Override
		public void onProtocolMessage(IProtocolMessage protocolMessage) {
			if(protocolMessage instanceof AccProtocolMessage) {
				long accChecksum = ((AccProtocolMessage) protocolMessage).getAccChecksum();
				synchronized (checksums) {
					checksums.remove(accChecksum);
				}
			}
		}
		
		public boolean isAcced(long checksum) {
			synchronized (checksums) {
				return !checksums.contains(checksum);
			}
		}
	}
}
