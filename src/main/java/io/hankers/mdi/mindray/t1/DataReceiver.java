package io.hankers.mdi.mindray.t1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.hankers.mdi.mdi_utils.MDILog;
import io.hankers.mdi.mindray.t1.Models.HL7Message;
import io.hankers.mdi.mindray.t1.Models.HL7Utils;
import io.hankers.mdi.mindray.t1.Models.VitalSign;
import io.hankers.mdi.mindray.t1.Models.Wave;

public class DataReceiver extends Thread {
	Socket _socket;
	private byte[] _buf = new byte[2048];
	String _ip;
	int _port = 4601;
	HL7Message _cachedMsg;

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
				InputStream ins = _socket.getInputStream();

				int readCount = 0;
				while (!Thread.currentThread().isInterrupted()) {
					readCount = ins.read(_buf, offset, _buf.length - offset);

					final int availableCount = offset + readCount;

					int unprocessed = cutHl7(_buf, availableCount);
					if (unprocessed > 0 && availableCount > unprocessed) {
						int m = 0;
						for (int i = unprocessed; i < availableCount; i++) {
							_buf[m++] = _buf[unprocessed];
						}
						offset = availableCount - unprocessed;
					} else {
						offset = 0;
					}

					// MUST set the correct encoding charset
					// cachedStr = cachedStr.concat(new String(_buf, 0, readCount,
					// StandardCharsets.US_ASCII));
				}
				ins.close();
			} catch (Exception e) {
				MDILog.e(e);
			}
		}
	}

	private int cutHl7(byte[] buf, int count) {
		int pos0B = -1;
		int pos1C = -1;
		int last1C = -1;
		for (int i = 0; i < count && i < buf.length; i++) {
			switch (buf[i]) {
			case 0x0B:
				pos0B = i;
				break;
			case 0x1C:
				pos1C = i;
				if (pos0B >= 0 && pos1C >= 0) {
					last1C = i;
					processHl7(buf, pos0B + 1, pos1C - pos0B - 1);
				}
				break;
			}
		}
		return last1C + 1;
	}

	private void processHl7(byte[] buf, int offset, int length) {
		HL7Message newMsg = HL7Utils.create(buf, offset, length);

		if (newMsg == null || newMsg.isEmpty()) {
			// do nothing
		} else if (newMsg instanceof Wave) {
			newMsg.publish();
		} else if (_cachedMsg == null) {
			_cachedMsg = newMsg;
		} else if (_cachedMsg._timestamp == newMsg._timestamp) {
			VitalSign vsMsg = (VitalSign) _cachedMsg;
			VitalSign vsNewMsg = (VitalSign) newMsg;
			if (vsMsg != null && vsNewMsg != null) {
				if (HL7Utils.isValidValue(vsNewMsg.co2)) {
					vsMsg.co2 = vsNewMsg.co2;
				}
				if (HL7Utils.isValidValue(vsNewMsg.dia)) {
					vsMsg.dia = vsNewMsg.dia;
				}
				if (HL7Utils.isValidValue(vsNewMsg.hr)) {
					vsMsg.hr = vsNewMsg.hr;
				}
				if (HL7Utils.isValidValue(vsNewMsg.mean)) {
					vsMsg.mean = vsNewMsg.mean;
				}
				if (HL7Utils.isValidValue(vsNewMsg.pvc)) {
					vsMsg.pvc = vsNewMsg.pvc;
				}
				if (HL7Utils.isValidValue(vsNewMsg.rr)) {
					vsMsg.rr = vsNewMsg.rr;
				}
				if (HL7Utils.isValidValue(vsNewMsg.spo2)) {
					vsMsg.spo2 = vsNewMsg.spo2;
				}
				if (HL7Utils.isValidValue(vsNewMsg.sys)) {
					vsMsg.sys = vsNewMsg.sys;
				}
				if (HL7Utils.isValidValue(vsNewMsg.temp)) {
					vsMsg.temp = vsNewMsg.temp;
				}
			}
		} else {
			// MDILog.d("publishing {}, {}", _cachedMsg, newMsg);
			_cachedMsg.publish();
			_cachedMsg = newMsg;
		}
	}

	public static class HeartBeat extends Thread {
		Socket _sk;
		byte[] _content = "MSH|^~\\&|||||||ORU^R01|106|P|2.3.1|\r".getBytes(StandardCharsets.US_ASCII);
		static SimpleDateFormat _sdf = new SimpleDateFormat("MMddHHmmssSSS");

		public HeartBeat(Socket sk) {
			_sk = sk;
		}

		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					OutputStream os = _sk.getOutputStream();
					os.write(0x0B);
					os.write(_content);
					os.write(0x1C);
					os.write(0x0D);

					String ts = _sdf.format(new Date());
					String queryWave = "MSH|^~\\&|||||||QRY^R02|1203|P|2.3.1\rQRD|" + ts + "|R|I|Q" + ts
							+ "|||||RES\rQRF|MON||||0&0^1^1^1^\r";
					os.write(0x0B);
					os.write(queryWave.getBytes(StandardCharsets.US_ASCII));
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
