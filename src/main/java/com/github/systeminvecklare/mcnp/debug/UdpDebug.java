package com.github.systeminvecklare.mcnp.debug;

import java.net.DatagramPacket;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class UdpDebug {
	public static long getChecksum(DatagramPacket datagramPacket) {
		Checksum checksum = new Adler32();
		checksum.update(datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength());
		return checksum.getValue();
	}
}
