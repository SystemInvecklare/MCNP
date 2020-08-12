package com.github.systeminvecklare.mcnp;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing resources that needs to be released.
 *  
 * @author Mattias Selin
 *
 */
/*package-protected*/ class ResourceHolder implements IReleasable {
	private final ResourceHolder parent; 
	private boolean released = false;
	private final List<ReleaseStage> releaseStages = new ArrayList<>();
	
	public ResourceHolder(ResourceHolder parent) {
		this.parent = parent;
		if(parent != null) {
			parent.addReleasable(this);
		}
	}
	
	public ResourceHolder() {
		this(null);
	}
	
	public synchronized <T extends IReleasable> T addReleasable(T releasable) {
		return addReleasable(releasable, 0);
	}
	
	public synchronized <T extends IReleasable> T addReleasable(T releasable, int stage) {
		if(released) {
			releasable.release();
		} else {
			getOrCreateStage(stage).releasables.add(releasable);
		}
		return releasable;
	}
	
	public synchronized void removeReleasable(IReleasable releasable) {
		if(!released) {
			for(ReleaseStage releaseStage : releaseStages) {
				releaseStage.releasables.remove(releasable);
			}
		}
	}
	
	@Override
	public synchronized void release() {
		released = true;
		RuntimeException exception = null;
		for(ReleaseStage releaseStage : releaseStages) {
			for(IReleasable releasable : releaseStage.releasables) {
				try {
					releasable.release();
				} catch(RuntimeException e) {
					if(exception == null) {
						exception = e;
					} else {
						exception.addSuppressed(e);
					}
				}
			}
			releaseStage.releasables.clear();
		}
		releaseStages.clear();
		if(parent != null) {
			parent.removeReleasable(this);
		}
		if(exception != null) {
			throw exception;
		}
	}
	
	private ReleaseStage getOrCreateStage(int stage) {
		for(int i = 0; i < releaseStages.size(); ++i) {
			ReleaseStage releaseStage = releaseStages.get(i);
			if(releaseStage.stage == stage) {
				return releaseStage;
			} else if(stage < releaseStage.stage) {
				ReleaseStage newStage = new ReleaseStage(stage);
				releaseStages.add(i, newStage);
				return newStage;
			}
		}
		ReleaseStage newStage = new ReleaseStage(stage);
		releaseStages.add(newStage);
		return newStage;
	}

	public static IReleasable createReleasable(Closeable closeable) {
		return new IReleasable() {
			@Override
			public void release() {
				try {
					closeable.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
	
	private static class ReleaseStage {
		private final int stage;
		private final List<IReleasable> releasables = new ArrayList<>();

		public ReleaseStage(int stage) {
			this.stage = stage;
		}
	}
}
