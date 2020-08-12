package com.github.systeminvecklare.mcnp;

/*package-protected*/ class AutoLooper implements IReleasable {
	private final Runnable loop;
	private final IRunCondition runCondition;
	private volatile Thread activeThread = null;
	private final Object activeThreadLifeCycle = new Object(); //Mutex
	private final Object activeThreadSwitch = new Object(); //Mutex
	private volatile boolean released = false;
	
	public AutoLooper(Runnable loop, IRunCondition runCondition) {
		this.loop = loop;
		this.runCondition = runCondition;
	}
	
	public synchronized void checkCondition() {
		if(runCondition.isMet() && !released) {
			if(!isRunning()) {
				start();
			}
		} else {
			if(isRunning()) {
				stop();
			}
		}
	}
	
	private synchronized boolean isRunning() {
		return activeThread != null;
	}
	
	private synchronized void stop() {
		synchronized (activeThreadSwitch) {
			activeThread = null;
		}
	}
	
	private synchronized void start() {
		synchronized (activeThreadSwitch) {
			activeThread = new Thread() {
				@Override
				public void run() {
					synchronized (activeThreadLifeCycle) {
						onBeforeStart();
						mainLoop: while(runCondition.isMet() && !released && (activeThread == Thread.currentThread())) {
							try {
								loop.run();
							} catch(RuntimeException e) {
								if(released || onRuntimeException(e)) {
									break mainLoop;
								}
							}
						}
						onAfterStopped();
					}
					synchronized (activeThreadSwitch) {
						if(activeThread == Thread.currentThread()) {
							activeThread = null;
						}
					}
				}
			};
			activeThread.start();
		}
	}

	
	protected void onBeforeStart() {
	}
	
	protected void onAfterStopped() {
	}

	/**
	 * 
	 * @param e
	 * @return if the Looper should be stopped
	 */
	protected boolean onRuntimeException(RuntimeException e) {
		e.printStackTrace();
		return false;
	}
	
	@Override
	public synchronized void release() {
		released = true;
		checkCondition();
	}
	
	public interface IRunCondition {
		boolean isMet();
	}
}
