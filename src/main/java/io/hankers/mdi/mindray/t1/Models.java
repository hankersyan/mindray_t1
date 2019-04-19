package io.hankers.mdi.mindray.t1;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONObject;

import io.hankers.mdi.mdi_utils.MDILog;
import io.hankers.mdi.mdi_utils.MqttPublisher;

public class Models {

	public static class HL7Message {
		String _type;
		int _controlId;
		long _timestamp;

		public void readSegment(String segment) {
		}

		public void publish() {
		};
	}

	public static class HL7Utils {

		public static HL7Message create(byte[] buf, int length) {
			boolean b = buf[0] == 0x0B;
			length = Math.min(buf.length, length);
			String str = new String(buf, b ? 1 : 0, b ? length - 1 : length);
			MDILog.t(str);

			HL7Message ret = null;
			String[] strs = str.split("\\r");
			for (String tmp : strs) {
				if (tmp.startsWith("MSH")) {
					if (tmp.indexOf("|ADT^A01|101|") > 0) {
						ret = new ConnectIndication();
					} else if (tmp.indexOf("|ORU^R01|157|") > 0) {
						ret = new Wave();
					} else {
						ret = new VitalSign();
					}
				} else {
					ret.readSegment(tmp);
				}
			}
			return ret;
		}

		public static String readField(String[] fields, int index) {
			if (fields.length > index && fields[index] != null) {
				return fields[index];
			}
			return null;
		}

	}

	public static class ConnectIndication extends HL7Message {

		Date _admittedDate;
		String _pGuid;
		String _pName;
		String _patientId;
		String _patientClass;
		String _patientType;
		String _ip;
		int _port;
		boolean _admitted;
		SimpleDateFormat _sdf = new SimpleDateFormat("yyyyMMdd");

		public ConnectIndication() {
			_type = "ADT^A01";
			_controlId = 101;
		}

		public void readSegment(String segment) {
			String[] fields = segment.split("\\|");
			try {
				if (segment.startsWith("EVN")) {
					if (fields.length > 3 && fields[2] != null && !fields[2].isEmpty()) {
						_admittedDate = _sdf.parse(fields[2]);
					}
				} else if (segment.startsWith("PID")) {
					_pGuid = HL7Utils.readField(fields, 4);
					_pName = HL7Utils.readField(fields, 6);
				} else if (segment.startsWith("PV1")) {
					_patientClass = HL7Utils.readField(fields, 2);
					String pv1 = HL7Utils.readField(fields, 3);
					if (pv1 != null && !pv1.isEmpty()) {
						String[] components = pv1.split("^");
						if (components != null && components.length > 2) {
							String[] subcomps = components[2].split("&");
							if (subcomps != null && subcomps.length == 6) {
								int ip = Integer.valueOf(subcomps[2]);
								int ip0 = ip >> 24;
								int ip1 = ip >> 16;
								int ip2 = ip >> 8;
								int ip3 = ip & 0xFF;
								_ip = String.format("%d.%d.%d.%d", ip0, ip1, ip2, ip3);
								_port = Integer.valueOf(subcomps[3]);
								_admitted = subcomps[5] == "1";
							}
						}
					}
				}
			} catch (ParseException e) {
				MDILog.w(e);
			}
		}
	}

	public static class VitalSign extends HL7Message {
		// |ORU^R01|204| for Periodic vital sign
		String hr; // 101
		String pvc; // 102
		String rr; // 151
		String temp; // 200
		String spo2; // 160
		String co2; // 220
		// |ORU^R01|503| for NIBP
		String sys; // 170
		String dia; // 171
		String mean; // 172
		static SimpleDateFormat _sdf = new SimpleDateFormat("yyyyMMddHHmmss");

		public VitalSign() {
			_type = "ORU^R01";
			_controlId = 204; // 204 or 503
		}

		public void readSegment(String segment) {
			String[] fields = segment.split("\\|");
			try {
				if (segment.startsWith("OBX")) {
					String idName = HL7Utils.readField(fields, 3);
					String val = HL7Utils.readField(fields, 5);
					if (val != null && !val.isEmpty()) {
						if (idName.startsWith("101^")) {
							hr = val;
						} else if (idName.startsWith("102^")) {
							pvc = val;
						} else if (idName.startsWith("151^")) {
							rr = val;
						} else if (idName.startsWith("200^")) {
							temp = val;
						} else if (idName.startsWith("160^")) {
							spo2 = val;
						} else if (idName.startsWith("220^")) {
							co2 = val;
						} else if (idName.startsWith("170^")) {
							sys = val;
						} else if (idName.startsWith("171^")) {
							dia = val;
						} else if (idName.startsWith("172^")) {
							mean = val;
						}
					}
					if (fields.length > 13) {
						Date d = _sdf.parse(fields[14]);
						_timestamp = d.getTime();
					} else {
						_timestamp = new Date().getTime() / 1000 * 1000;
					}
				}
			} catch (Exception e) {
				MDILog.w(e);
			}
		}

		public void publish() {
			JSONObject json = new JSONObject();
			if (hr != null && !hr.isEmpty() && !hr.startsWith("-100")) {
				json.put("HEART_BEAT", hr);
			}
			if (sys != null && !sys.isEmpty() && !sys.startsWith("-100")) {
				json.put("NBP_SYS", sys);
			}
			if (dia != null && !dia.isEmpty() && !dia.startsWith("-100")) {
				json.put("NBP_DIA", dia);
			}
			if (mean != null && !mean.isEmpty() && !mean.startsWith("-100")) {
				json.put("NBP_MEAN", mean);
			}
			if (rr != null && !rr.isEmpty() && !rr.startsWith("-100")) {
				json.put("RESP_RATE", rr);
			}
			if (spo2 != null && !spo2.isEmpty() && !spo2.startsWith("-100")) {
				json.put("SPO2", spo2);
			}
			if (temp != null && !temp.isEmpty() && !temp.startsWith("-100")) {
				json.put("TEMP", temp);
			}
			if (!json.isEmpty()) {
				if (_timestamp > 0) {
					json.put("timestamp", _timestamp);
				}
				MqttPublisher.addMessage(json.toString());
			}
		}
	}

	public static class Wave extends HL7Message {
		// |ORU^R01|157|
	}
}
