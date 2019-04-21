package io.hankers.mdi.mindray.t1;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

import io.hankers.mdi.mdi_utils.MDILog;
import io.hankers.mdi.mindray.t1.Models.ConnectIndication;
import io.hankers.mdi.mindray.t1.Models.HL7Utils;

public class MonitorSniffer extends Thread {
	private DatagramSocket _udp;
	private byte[] _cbuf = new byte[2048];
	final int PORT = 4600; // 4679 for center or gateway

	public MonitorSniffer() {
	}

	public void run() {
		DatagramPacket packet = new DatagramPacket(_cbuf, _cbuf.length);
		while (!Thread.currentThread().isInterrupted()) {
			try {
				_udp = new DatagramSocket(PORT);
			} catch (SocketException e1) {
				MDILog.w(e1);
			}

			while (!Thread.currentThread().isInterrupted()) {
				try {
					Arrays.fill(_cbuf, (byte) 0);
					_udp.receive(packet);

					InetAddress address = packet.getAddress();
					int port = packet.getPort();

					System.out.format("Connection Indication from %s:%d", address.getHostAddress(), port);

					ConnectIndication conn = (ConnectIndication) HL7Utils.create(_cbuf, 0, packet.getLength());

					if (conn._ip != null && !conn._ip.isEmpty()) {
						new DataReceiver(conn._ip, conn._port).start();

						_udp.close();

						return;
					}
				} catch (IOException e) {
					MDILog.d("Sniffering", e);
				}
			}
		}
	}

}