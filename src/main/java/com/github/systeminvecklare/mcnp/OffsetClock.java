package com.github.systeminvecklare.mcnp;

import com.github.systeminvecklare.mcnp.time.IClock;

/*package-protected*/ class OffsetClock implements IClock {
	private final long offset;
	private final IClock clock;
	

	public OffsetClock(long offset, IClock clock) {
		this.offset = offset;
		this.clock = clock;
	}

	@Override
	public long getTime() {
		return clock.getTime()+offset;
	}
}
