package com.github.systeminvecklare.mcnp;

import com.github.systeminvecklare.mcnp.AutoLooper.IRunCondition;

/*package-protected*/ class Looper implements IReleasable {
	private final AutoLooper autoLooper;
	private volatile boolean released = false;
	private volatile boolean started = false;
	
	public Looper(Runnable loop) {
		this.autoLooper = new AutoLooper(loop, new IRunCondition() {
			@Override
			public boolean isMet() {
				return started && !released;
			}
		}) {
			@Override
			protected void onBeforeStart() {
				Looper.this.onBeforeStart();
			}
			
			@Override
			protected void onAfterStopped() {
				Looper.this.onAfterStopped();
			}
			
			@Override
			protected boolean onRuntimeException(RuntimeException e) {
				return Looper.this.onRuntimeException(e);
			}
		};
	}
	
	
	public synchronized void start() {
		started = true;
		autoLooper.checkCondition();
	}
	
	protected void onBeforeStart() {
	}
	
	protected void onAfterStopped() {
	}

	public synchronized void stop() {
		started = false;
		autoLooper.checkCondition();
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
		autoLooper.release();
	}
}
