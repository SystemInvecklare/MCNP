package com.github.systeminvecklare.mcnp;

/*package-protected*/ interface IProtocolMessageEventSource {
	void addListener(IProtocolMessageListener listener);
	void removeListener(IProtocolMessageListener listener);
	void replaceListener(IProtocolMessageListener oldListener, IProtocolMessageListener newListener);
}
