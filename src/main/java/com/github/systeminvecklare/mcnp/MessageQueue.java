package com.github.systeminvecklare.mcnp;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/*package-protected*/ class MessageQueue<T> implements IReleasable {
	private int maxLength = 100;
	private final Queue<T> messages = new LinkedList<T>();
	private boolean released = false;
	
	public synchronized void queueMessage(T message) {
		if(!released) {
			if(messages.size() < maxLength) {
				messages.add(message);
			} else {
				System.err.println("Message queue overflow");
//				throw new RuntimeException("Message queue overflow"); //TODO have specific error?
			}
		}
	}
	
	public synchronized T poll() {
		if(released) {
			return null;
		}
		return messages.poll();
	}
	
	public boolean harvest(Collection<? super T> harvestedMessages) {
		boolean atLeastOne = false;
		synchronized (this) {
			while(!messages.isEmpty()) {
				harvestedMessages.add(messages.poll());
				atLeastOne = true;
			}
		}
		return atLeastOne;
	}
	
	public synchronized void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	@Override
	public synchronized void release() {
		messages.clear();
		released = true;
	}
}
