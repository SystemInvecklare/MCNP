package com.github.systeminvecklare.mcnp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.github.systeminvecklare.mcnp.time.IClock;

/**
 * Utility class to keep track of received messaged
 * @author Mattias Selin
 *
 */
/*package-protected*/ class UuidCache implements IReleasable {
	private volatile boolean released = false;
	private final IClock clock;
	private final Map<UUID, Long> cache = new HashMap<UUID, Long>();

	public UuidCache(IClock clock) {
		this.clock = clock;
	}

	public synchronized void addUuid(long expiryTime, UUID uuid) {
		if(released) {
			return;
		}
		if(clock.getTime() <= expiryTime) {
			cache.put(uuid, expiryTime);
		}
	}
	
	public synchronized boolean hasUUID(UUID uuid) {
		return cache.containsKey(uuid);
	}
	
	public synchronized void prune() {
		Iterator<Entry<UUID, Long>> iterator = cache.entrySet().iterator();
		while(iterator.hasNext()) {
			Entry<UUID, Long> entry = iterator.next();
			if(clock.getTime() > entry.getValue().longValue()) {
				iterator.remove();
			}
		}
	}
	
	@Override
	public synchronized void release() {
		released = true;
		cache.clear();
	}
}
