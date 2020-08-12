package com.github.systeminvecklare.mcnp;

//TODO Remember: this is for actual McnpMessages (i.e. messages the user sends with Mcnp)
public interface IMcnpMessageListener {
	void onMessage(McnpMessage mcnpMessage); 
}
