package com.github.systeminvecklare.mcnp.debug;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SyncTracker {
	private final UUID uuid = UUID.randomUUID();
	
	private final ThreadLocal<AtomicInteger> syncs = ThreadLocal.withInitial(new Supplier<AtomicInteger>() {
		@Override
		public AtomicInteger get() {
			return new AtomicInteger(0);
		}
	});
	
	
	public void syncStart() {
		if(syncs.get().incrementAndGet() == 1) {
			StringBuilder builder = new StringBuilder();
			for(StackTraceElement ste : Thread.currentThread().getStackTrace()) {
				builder.append(ste).append("\n");
			}
			System.out.println(Thread.currentThread()+" getting lock on "+uuid+" at "+builder.toString());	
		}
	}
	
	public void syncEnd() {
		if(syncs.get().decrementAndGet() <= 0) {
			System.out.println(Thread.currentThread()+" releasing lock on "+uuid);
		}
	}
}
