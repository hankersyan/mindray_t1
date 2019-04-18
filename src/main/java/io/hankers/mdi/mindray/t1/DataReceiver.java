package io.hankers.mdi.mindray.t1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import io.hankers.mdi.mdi_utils.MDILog;
import io.hankers.mdi.mindray.t1.Models.HL7Message;
import io.hankers.mdi.mindray.t1.Models.HL7Utils;

public class DataReceiver extends Thread {
	Socket _socket;
	private char[] _cbuf = new char[2048];
	String _ip;
	int _port = 4601;

	public DataReceiver(String ip, int port) throws UnknownHostException, IOException {
		_ip = ip;
		_port = port;
	}

	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				_socket = new Socket(_ip, _port);

				new HeartBeat(_socket).start();
				work();
			} catch (IOException e1) {
				MDILog.w(e1);

				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					MDILog.w(e);
					break;
				}
			}
		}

		try {
			_socket.close();
		} catch (Exception e) {
			MDILog.w(e);
		}
	}

	private void work() {
		int offset = 0;
		while (!Thread.currentThread().isInterrupted()) {
			try {
				// PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(_socket.getInputStream()));

				int readCount = 0;
				while (!Thread.currentThread().isInterrupted()) {
					readCount = in.read(_cbuf, offset, _cbuf.length - offset);

					final int availableCount = offset + readCount;

					int unprocessed = cutHl7(_cbuf, availableCount);
					if (unprocessed > 0 && availableCount > unprocessed) {
						int m = 0;
						for (int i = unprocessed; i < availableCount; i++) {
							_cbuf[m++] = _cbuf[unprocessed];
						}
						offset = availableCount - unprocessed;
					} else {
						offset = 0;
					}
				}
				in.close();
			} catch (Exception e) {
				MDILog.e(e);
			}
		}
	}

	private int cutHl7(char[] buf, int count) {
		int pos0B = -1;
		int pos1C = -1;
		int last1C = -1;
		for (int i = 0; i < count && i < buf.length; i++) {
			switch (_cbuf[i]) {
			case 0x0B:
				pos0B = i;
				break;
			case 0x1C:
				pos1C = i;
				if (pos0B >= 0 && pos1C >= 0) {
					last1C = i;
					processHl7(new String(buf, pos0B + 1, pos1C - pos0B - 1));
				}
				break;
			}
		}
		return last1C + 1;
	}

	private void processHl7(String hl7) {
		HL7Message msg = HL7Utils.create(hl7.getBytes(), hl7.length());
		msg.publish();
	}

	public static class HeartBeat extends Thread {
		Socket _sk;
		byte[] _content = "MSH|^~\\&|||||||ORU^R01|106|P|2.3.1|".getBytes();
		public HeartBeat(Socket sk) {
			_sk = sk;
		}
		public void run() {
			while(!Thread.currentThread().isInterrupted()) {
				try {
					OutputStream os = _sk.getOutputStream();
					os.write(0x0B);
					os.write(_content);
					os.write(0x1C);
					os.write(0x0D);
				} catch (IOException e) {
					MDILog.e(e);
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					MDILog.e(e);
				}
			}
		}
	}
}
