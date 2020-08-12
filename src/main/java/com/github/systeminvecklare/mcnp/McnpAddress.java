package com.github.systeminvecklare.mcnp;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public final class McnpAddress {
	/*package-protected*/ final InetAddress address;
	/*package-protected*/ final int port;
	
	public McnpAddress(InetAddress address, int port) {
		this.address = address;
		this.port = port;
	}
	
	public McnpAddress(String host, int port) throws UnknownHostException {
		this(InetAddress.getByName(host), port);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof McnpAddress) {
			McnpAddress other = (McnpAddress) obj;
			return Objects.equals(this.port, other.port) && Objects.equals(this.address, other.address);
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(address, port);
	}

	/*package-protected*/ void stamp(DatagramPacket packet) {
		packet.setAddress(address);
		packet.setPort(port);
	}
	
	public InetAddress getAddress() {
		return address;
	}
	
	public int getPort() {
		return port;
	}
	
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(address).append(":").append(port);
		return stringBuilder.toString();
	}

	/*package-protected*/ static McnpAddress from(DatagramPacket datagramPacket) {
		return new McnpAddress(datagramPacket.getAddress(), datagramPacket.getPort());
	}
}
