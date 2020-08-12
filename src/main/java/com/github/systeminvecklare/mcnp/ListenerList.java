package com.github.systeminvecklare.mcnp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*package-protected*/ final class ListenerList<L> {
	private final List<ListenerLink> listeners = new ArrayList<>();

	public void addListener(L listener) {
		synchronized (listeners) {
			listeners.add(new ListenerLink(listener));
		}
	}
	
	public void removeListener(L listener) {
		synchronized (listeners) {
			Iterator<ListenerLink> iterator = listeners.iterator();
			while(iterator.hasNext()) {
				ListenerList<L>.ListenerLink listenerLink = iterator.next();
				if(listenerLink.listener.equals(listener)) {
					listenerLink.removed = true;
					iterator.remove();
				}
			}
		}
	}

	public void clear() {
		synchronized (listeners) {
			for(ListenerList<L>.ListenerLink link : listeners) {
				link.removed = true;
			}
			listeners.clear();
		}
	}
	
	public boolean isEmpty() {
		synchronized (listeners) {
			return listeners.isEmpty();
		}
	}

	public void replaceListener(L oldListener, L newListener) {
		synchronized (listeners) {
			removeListener(oldListener);
			addListener(newListener);
		}
	}
	
	public void forEach(IEvent<L> event) {
		List<ListenerLink> listenersSnapshot;
		synchronized (listeners) {
			listenersSnapshot = new ArrayList<>(listeners);
		}
		for(ListenerLink link : listenersSnapshot) {
			if(!link.removed) {
				event.fireFor(link.listener);
			}
		}
	}
	
	private class ListenerLink {
		private volatile boolean removed = false;
		private final L listener;
		
		public ListenerLink(L listener) {
			this.listener = listener;
		}
	}
}
