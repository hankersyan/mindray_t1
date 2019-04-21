package io.hankers.mdi.mindray.t1;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONObject;

import io.hankers.mdi.mdi_utils.MDILog;
import io.hankers.mdi.mdi_utils.MqttPublisher;

public class Models {

	public static class HL7Message {
		String _type;
		int _controlId;
		long _timestamp;

		public boolean isEmpty() {
			return false;
		}

		public void readSegment(byte[] buf, int offset, int length) {
		}

		public void publish() {
		};
	}

	public static class HL7Utils {

		public static HL7Message create(byte[] buf, int offset, int length) {
			HL7Message ret = null;
			int start = offset;
			int end = offset + length;
			for (int i = offset; i < end; i++) {
				if (buf[i] == '\r') {
					String tmp = new String(buf, start, i - start, StandardCharsets.US_ASCII);
					if (tmp.startsWith("MSH")) {
						if (tmp.indexOf("|ADT^A01|101|") > 0) {
							ret = new ConnectIndication();
						} else if (tmp.indexOf("|ORU^R01|157|") > 0) {
							ret = new Wave();
						} else {
							ret = new VitalSign();
						}
					} else {
						ret.readSegment(buf, start, i - start);
					}
					start = i + 1;
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

		public static boolean isValidValue(String val) {
			return val != null && !val.isEmpty() && !val.startsWith("-100");
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

		public void readSegment(byte[] buf, int offset, int length) {
			String segment = new String(buf, offset, length);
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

		public void readSegment(byte[] buf, int offset, int length) {
			String segment = new String(buf, offset, length);
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
			String content = this.toString();
			if (content != null && !content.isEmpty()) {
				MqttPublisher.addMessage(content);
			}
		}

		public String toString() {
			JSONObject json = new JSONObject();
			if (HL7Utils.isValidValue(hr)) {
				json.put("HEART_BEAT", hr);
			}
			if (HL7Utils.isValidValue(sys)) {
				json.put("NBP_SYS", sys);
			}
			if (HL7Utils.isValidValue(dia)) {
				json.put("NBP_DIA", dia);
			}
			if (HL7Utils.isValidValue(mean)) {
				json.put("NBP_MEAN", mean);
			}
			if (HL7Utils.isValidValue(rr)) {
				json.put("RESP_RATE", rr);
			}
			if (HL7Utils.isValidValue(spo2)) {
				json.put("SPO2", spo2);
			}
			if (HL7Utils.isValidValue(temp)) {
				json.put("TEMP", temp);
			}
			if (!json.isEmpty()) {
				if (_timestamp > 0) {
					json.put("timestamp", _timestamp);
				}
				return json.toString();
			}
			return null;
		}

		@Override
		public boolean isEmpty() {
			String content = this.toString();
			return content == null || content.isEmpty();
		}
	}

	public static class Wave extends HL7Message {
		// |ORU^R01|157|
		int _sCheckSum;
		int _sWaveId;
		byte _dataSize;
		int _sSampleRate;
		byte _reserve;
		Map<Integer, int[]> _values; // unsigned byte or unsigned short

		public Wave() {
			_type = "ORU^R01";
			_controlId = 157;
			_values = new HashMap<Integer, int[]>();
		}

		public void readSegment(byte[] buf, int offset, int length) {
			String segment = new String(buf, offset, length);
			String[] fields = segment.split("\\|");
			try {
				if (segment.startsWith("ZMW")) {
					String val = HL7Utils.readField(fields, 1);
					if (val != null && !val.isEmpty() && val.length() > 7) {
						_timestamp = new Date().getTime();

						// can NOT use val.getBytes() because of encoding-charset
						byte[] origins = new byte[length];
						origins = Arrays.copyOfRange(buf, offset + 4, offset + length);

						List<Short> decoded = decode(origins);
						short[] unsignedBytes = ArrayUtils.toPrimitive(decoded.toArray(new Short[decoded.size()]));
						_sCheckSum = 0;
						byte[] tmp1 = new byte[] { (byte) unsignedBytes[2], (byte) unsignedBytes[3] };
						_sWaveId = ByteBuffer.wrap(tmp1).order(ByteOrder.BIG_ENDIAN).getShort();
						_dataSize = (byte) unsignedBytes[4];
						byte[] tmp2 = new byte[] { (byte) unsignedBytes[5], (byte) unsignedBytes[6] };
						_sSampleRate = ByteBuffer.wrap(tmp2).order(ByteOrder.BIG_ENDIAN).getShort();
						_reserve = 0;
						MDILog.d("Wave : tmpStr.length={}, _sWaveId={}, _dataSize={}, _sSampleRate={}", val.length(),
								_sWaveId, _dataSize, _sSampleRate);
						if (_dataSize == 1) {
							int[] values = new int[_dataSize * _sSampleRate];
							if (values.length + 8 >= decoded.size()) {
								MDILog.e("Error length in readSegment 1");
							}
							for (int i = 0; i < values.length; i++) {
								values[i] = decoded.get(8 + i).shortValue();
							}
							_values.put(_sWaveId, values);
						} else if (_dataSize == 2) {
							int[] values = new int[_dataSize * _sSampleRate / 2];
							if (values.length * 2 + 8 >= decoded.size()) {
								MDILog.e("Error length in readSegment 2");
							}
							int shortCount = (decoded.size() - 8) / 2;
							for (int i = 0; i < shortCount; i++) {
								byte[] tmp = new byte[] { decoded.get(8 + i * 2).byteValue(),
										decoded.get(8 + i * 2 + 1).byteValue() };
								values[i] = ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN).getInt();
							}
							_values.put(_sWaveId, values);
						}
					}
				}
			} catch (Exception e) {
				MDILog.w(e);
			}
		}

		// https://stackoverflow.com/questions/4266756/can-we-make-unsigned-byte-in-java
		// convert byte to unsigned
		// 1. Byte.toUnsignedInt
		// 2. &0xFF
		private List<Short> decode(byte[] src) {
			List<Short> ret = new ArrayList<Short>();
			int blockCount = src.length / 8;
			for (int i = 0; i < blockCount; i++) {
				int endByte = src[i * 8 + 7];
				for (int n = 0; n < 7; n++) {
					byte b = (byte) ((endByte >> n) & 0x1);
					ret.add((short) (b > 0 ? (src[i * 8 + n] & 0xFF) : (src[i * 8 + n] & 0x7F)));
				}
			}
			int remainders = src.length - blockCount * 8;
			if (remainders > 0) {
				MDILog.d("hai zhen you remainders");
			}
			return ret;
		}

		public void publish() {
			if (!isEmpty()) {
				String content = this.toString();
				MqttPublisher.addMessage(content);
			}
		}

		public String toString() {
			JSONObject json = new JSONObject();
			if (!isEmpty()) {
				for (Integer key : _values.keySet()) {
					String waveName = getWaveName(key);
					if (waveName != null && !waveName.isEmpty()) {
						json.put(waveName, resample128(_values.get(key)));
					} else {
						MDILog.i("NULL Name for {}", key);
					}
				}
			}
			if (!json.isEmpty()) {
				if (_timestamp > 0) {
					json.put("timestamp", _timestamp);
				}
				return json.toString();
			}
			return null;
		}

		@Override
		public boolean isEmpty() {
			return _values == null || _values.size() == 0;
		}

		private int[] resample128(int[] src) {
			float step = (float) (src.length * 1.0 / 128);
			int[] ret = new int[128];
			for (int i = 0; i < ret.length; i++) {
				ret[i] = src[(int) (i * step)];
			}
			return ret;
		}

		private String getWaveName(int waveId) {
//			1101 ECG_CH1
//			1102 ECG_CH2
//			1103 ECG_CH3
//			1104 ECG_CH4
//			1105 ECG_CH5
//			1106 ECG_CH6
//			1107 ECG_CH7
//			1108 ECG_CH8
//			1109 ECG_CH9
//			1110 ECG_CH10
//			1111 ECG_CH11
//			1112 ECG_CH12
//			1113 ECG_I
//			1114 ECG_II
//			1115 ECG_III
//			1116 ECG_aVR
//			1117 ECG_aVL
//			1118 ECG_aVF
//			1119 ECG_V1
//			1120 ECG_V2
//			1121 ECG_V3
//			1122 ECG_V4
//			1123 ECG_V5
//			1124 ECG_V6
//			1125 ECG_CAL
//			1126 ECG_V
//			1127 ECG_PACE
//			1128 ECG_PADS
//			1129 ECG_PADDLES
//			1151 RESP
//			1152 PLETH
//			1161 IBP_CH1
//			1162 IBP_CH2
//			1163 IBP_CH3
//			1164 IBP_CH4
//			1171 IBP_ART
//			1172 IBP_PA
//			1173 IBP_CVP
//			1174 IBP_RAP
//			1175 IBP_LAP
//			1176 IBP_ICP
//			1177 IBP_P1
//			1178 IBP_P2
//			1179 IBP_P3
//			1180 IBP_P4
//			1181 IBP_P5
//			1182 IBP_P6
//			1183 IBP_P7
//			1184 IBP_P8
//			1185 IBP_Ao
//			1186 IBP_UAP
//			1187 IBP_BAP
//			1188 IBP_FAP
//			1189 IBP_UVP
//			1190 IBP_SD1
//			1191 IBP_SD2
//			1192 IBP_SD3
//			1193 IBP_SD4
//			1194 IBP_SD5
//			1195 IBP_SD6
//			1196 IBP_SD7
//			1197 IBP_SD8
//			1198 IBP_LVP
//			1201 IBP_pART
//			1202 IBP_pCVP
//			1200 CO2
//			1210 GAS_CO2
//			1211 GAS_O2
//			1212 GAS_N2O
//			1213 GAS_AA
//			1214 GAS_HAL
//			1215 GAS_ENF
//			1216 GAS_ISO
//			1217 GAS_SEV
//			1218 GAS_DES
//			1230 VENT_PAW
//			1231 VENT_FLOW
//			1232 VENT_VOLUME
//			1240 BIS_EEG
//			1241 BIS_SPECTRA
//			1242 BIS_EEG_LT
//			1243 BIS_EEG_LE
//			1244 BIS_EEG_RT
//			1245 BIS_EEG_RE
//			1250 RMPaw 气道压力 2 50 -20~120
//			1251 RMFlow 流速 2 50 -180~180
//			1252 RMVolume
//			1260 ICG_ICG
//			1261 ICG_IECG
//			1270 SPO2B_PLETH
			switch (waveId) {
			case 1101:
			case 1113:
				return "I";
			case 1102:
			case 1114:
				return "II";
			case 1103:
			case 1115:
				return "III";
			case 1104:
			case 1116:
				return "I";
			case 1105:
			case 1117:
				return "aVL";
			case 1106:
			case 1118:
				return "aVF";
			case 1107:
			case 1119:
				return "V1";
			case 1108:
			case 1120:
				return "V2";
			case 1109:
			case 1121:
				return "V3";
			case 1110:
			case 1122:
				return "V4";
			case 1111:
			case 1123:
				return "V5";
			case 1112:
			case 1124:
				return "V6";
			case 1151:
				return "RESP";
			case 1152:
				return "PLETH";
			}
			return null;
		}
	}
}
