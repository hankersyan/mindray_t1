package io.hankers.mdi.mindray.t1;

import io.hankers.mdi.mdi_utils.MDIConfig;
import io.hankers.mdi.mdi_utils.MDILog;

/**
 * Hello world!
 *
 */
public class App {
	public static void main(String[] args) {
		String ip = MDIConfig.getMonitorIp();
		if (ip != null && !ip.isEmpty()) {
			MDILog.i("Connecting " + ip);
			try {
				new DataReceiver(ip, 4601).start();
			} catch (Exception e) {
				MDILog.e(e);
			}
		} else {
			MDILog.i("Sniffering ");
			new MonitorSniffer().start();
		}
	}
}
