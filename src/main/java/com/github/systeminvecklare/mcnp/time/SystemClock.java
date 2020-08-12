package com.github.systeminvecklare.mcnp.time;

public class SystemClock implements IClock {
	@Override
	public long getTime() {
		return System.currentTimeMillis();
	}
}
