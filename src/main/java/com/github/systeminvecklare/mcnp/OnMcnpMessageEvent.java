package com.github.systeminvecklare.mcnp;

/*package-protected*/ final class OnMcnpMessageEvent implements IEvent<IMcnpMessageListener> {
	private final McnpMessage mcnpMessage;
	
	public OnMcnpMessageEvent(McnpMessage mcnpMessage) {
		this.mcnpMessage = mcnpMessage;
	}

	@Override
	public void fireFor(IMcnpMessageListener listener) {
		listener.onMessage(mcnpMessage);
	}
}
