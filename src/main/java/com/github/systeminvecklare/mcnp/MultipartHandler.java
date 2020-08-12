package com.github.systeminvecklare.mcnp;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.github.systeminvecklare.mcnp.IAllocator.IBorrowedByteArray;
import com.github.systeminvecklare.mcnp.time.IClock;

/*package-protected*/ class MultipartHandler implements IReleasable {
	private volatile boolean released = false;
	
	private final IAllocator allocator;
	private final IClock clock;
	private final UuidCache uuidCache;
	private final Map<UUID, MultipartConstruction> multipartConstructions = new HashMap<UUID, MultipartHandler.MultipartConstruction>();
	
	public MultipartHandler(IAllocator allocator, IClock clock, UuidCache uuidCache) {
		this.allocator = allocator;
		this.clock = clock;
		this.uuidCache = uuidCache;
	}


	public synchronized void supply(ConnectedProtocolMessage multipartMessage, Collection<OutgoingMcnpMessage> outgoingMcnpMessages) {
		if(released) {
			return;
		}
		UUID uuid = multipartMessage.getUuid();
		long expiryTime = multipartMessage.getExpiryTime();
		if(!uuidCache.hasUUID(uuid)) {
			MultipartConstruction multipartConstruction = multipartConstructions.get(uuid);
			if(multipartConstruction == null) {
				multipartConstruction = new MultipartConstruction(expiryTime, multipartMessage.getParts());
				multipartConstructions.put(uuid, multipartConstruction);
			}
			multipartConstruction.set(multipartMessage.getPartIndex(), multipartMessage.getPayload());
			if(multipartConstruction.isReady()) {
				multipartConstructions.remove(uuid);
				McnpMessage mcnpMessage = multipartConstruction.assembleAndRelease();
				outgoingMcnpMessages.add(new OutgoingMcnpMessage(expiryTime, uuid, mcnpMessage));
			}
		}
	}
	
	public synchronized void prune() {
		if(released) {
			return;
		}
		Iterator<Entry<UUID, MultipartConstruction>> iterator = multipartConstructions.entrySet().iterator();
		while(iterator.hasNext()) {
			Entry<UUID, MultipartConstruction> entry = iterator.next();
			if(entry.getValue().expiryTime < clock.getTime() || uuidCache.hasUUID(entry.getKey())) {
				entry.getValue().release();
				iterator.remove();
			}
		}
	}
	
	@Override
	public synchronized void release() {
		released = true;
		for(MultipartConstruction construction : multipartConstructions.values()) {
			if(construction != null) {
				construction.release();
			}
		}
		multipartConstructions.clear();
	}



	private class MultipartConstruction {
		private final long expiryTime;
		private final IAllocator.IBorrowedByteArray[] partsArray;
		private int totalSize = 0;

		public MultipartConstruction(long expiryTime, int parts) {
			this.expiryTime = expiryTime;
			partsArray = new IAllocator.IBorrowedByteArray[parts];
		}
		
		public void release() {
			for(IBorrowedByteArray part : partsArray) {
				if(part != null) {
					part.release();
				}
			}
			Arrays.fill(partsArray, null);
		}

		public void set(int index, ByteArray payload) {
			if(partsArray[index] == null) {
				IBorrowedByteArray chunk = allocator.obtain(payload.getLength());
				try {
					payload.copyTo(chunk.getByteArray());
				} catch (RuntimeException e) {
					chunk.release();
					throw e;
				}
				partsArray[index] = chunk;
				totalSize += payload.getLength();
			}
		}
		
		public boolean isReady() {
			for(IBorrowedByteArray part : partsArray) {
				if(part == null) {
					return false;
				}
			}
			return true;
		}
		
		public McnpMessage assembleAndRelease() {
			byte[] messageData = new byte[totalSize];
			ByteArray assembled = new ByteArray(messageData);
			
			int offset = 0;
			for(IBorrowedByteArray part : partsArray) {
				ByteArray partData = part.getByteArray();
				ByteArray.copy(partData, 0, assembled, offset, partData.getLength());
				offset += partData.getLength();
				part.release();
			}
			Arrays.fill(partsArray, null);
			
			return new McnpMessage(messageData);
		}
	} 
}
