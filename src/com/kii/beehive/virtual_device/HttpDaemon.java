package com.kii.beehive.virtual_device;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.kii.beehive.virtual_device.exceptions.VendorThingIDExistException;
import fi.iki.elonen.NanoHTTPD;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Created by Evan on 2016/11/14.
 */
public class HttpDaemon extends NanoHTTPD {

	public static final String MIME_JSON = "application/json; charset=utf-8";
	HashMap<String, HashMap<String, Device>> userMap = new HashMap<>();

	public static final int CODE_OK = 1;
	public static final int CODE_NO_PERMISSION = 2;
	public static final int CODE_MISSED_PARAMETERS = 3;
	public static final int CODE_VID_EXIST = 4;
	public static final int CODE_ALREADY_START = 5;
	public static final int CODE_FAILED = 6;
	public static final int CODE_NOT_START = 7;
	public static final int CODE_IO_EXCEPTION = 8;
	public static final int CODE_NOT_IMPL = 9;

	public HttpDaemon() throws IOException {
		super(Constants.HTTP_PORT);
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		System.out.println("\nRunning! Point your browsers to http://localhost:" + Constants.HTTP_PORT + "/ \n");
	}

	public static void main(String[] args) {
		//To trigger static method
		Device d = new Device(null);
		try {
			new HttpDaemon();
		} catch (IOException ioe) {
			System.err.println("Couldn't start server:\n" + ioe);
		}
	}


	protected String getParamValue(Map<String, List<String>> parameters, String key) {
		List<String> list = parameters.get(key);
		if (list == null || list.size() == 0) {
			return null;
		}
		String param = list.get(0);
		return param;
	}

	protected Response getErrResp(Response.IStatus status, int code, String msg) {
		JSONObject json = new JSONObject();
		try {
			json.put("code", code);
			json.put("msg", msg);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return newFixedLengthResponse(status, MIME_JSON, json.toString());
	}

	protected Response getEmptyOKResp() {
		JSONObject json = new JSONObject();
		json.put("code", CODE_OK);
		return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
	}

	protected String getUserId(String accessToken) {
		Request request = new Request.Builder()
				.url(Constants.BeehiveBaseUrl + "/users/me")
				.header("Content-Type", Constants.JSON.toString())
				.header("Authorization", "Bearer " + accessToken)
				.build();
		OkHttpClient client = new OkHttpClient();
		try (okhttp3.Response response = client.newCall(request).execute()) {
			if (response.isSuccessful()) {
				String result = response.body().string();
				LogUtil.debug(result);
				JSONObject json = new JSONObject(result);
				return json.getString("userID");
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return null;
	}

	@Override
	public Response serve(IHTTPSession session) {
		String uri = session.getUri();
		if (uri.startsWith("/")) {
			uri = uri.substring(1);
		}
		Map<String, List<String>> parameters = session.getParameters();
		String accessToken = getParamValue(parameters, "accessToken");
		if (accessToken == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed accessToken");
		}
		String userID = getUserId(accessToken);
		if (userID == null) {
			return getErrResp(Response.Status.UNAUTHORIZED, CODE_NO_PERMISSION, "accessToken is invalid");
		}
		if (Device.device_schemas == null) {
			updateSchemas(userID, parameters);
		}
		if (Device.device_schemas == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_FAILED, "The schemas are not ready");
		}
		switch (uri) {
			case "createNewVirtualDevice":
				return createNewVirtualDevice(userID, parameters);
			case "createNewVirtualDeviceFromVID":
				return createNewVirtualDeviceFromVID(userID, parameters);
			case "listDevices":
				return listDevices(userID, parameters);
			case "startDevice":
				return startDevice(userID, parameters);
			case "stopDevice":
				return stopDevice(userID, parameters);
			case "deleteDevice":
				return deleteDevice(userID, parameters);
			case "getMessageQueue":
				return getMessageQueue(userID, parameters);
			case "getStates":
				return getStates(userID, parameters);
			case "setUploadStatePeriod":
				return setUploadStatePeriod(userID, parameters);
			case "setUploadStateOnChanged":
				return setUploadStateOnChanged(userID, parameters);
			case "setMessageQueueSize":
				return setMessageQueueSize(userID, parameters);
			case "setGenRandomStatePeriod":
				return setGenRandomStatePeriod(userID, parameters);
			case "setState":
				return setState(userID, parameters);
			case "updateSchemas":
				return updateSchemas(userID, parameters);
		}
		return getErrResp(Response.Status.NOT_FOUND, CODE_FAILED, "The action is not supported");
	}


	/**
	 * http://localhost:8129/createNewVirtualDevice?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&typeCode=C
	 *
	 * @param parameters
	 * @return
	 */
	protected Response createNewVirtualDevice(String userID, Map<String, List<String>> parameters) {
		String accessToken = getParamValue(parameters, "accessToken");
		String deviceType = getParamValue(parameters, "deviceType");
		if (deviceType == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed deviceType");
		}
		try {
			Device device = Device.createNewVirtualDevice(accessToken, deviceType);
			if (device == null) {
				return getErrResp(Response.Status.INTERNAL_ERROR, CODE_FAILED, "Create failed, check the device type code");
			}
			JSONObject json = new JSONObject();
			json.put("code", CODE_OK);
			json.put("vendorThingID", device.vendorThingID);
			json.put("globalThingID", device.globalThingID);
			return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
		} catch (IOException e) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_IO_EXCEPTION, e.getMessage());
		}
	}


	/**
	 * http://localhost:8129/createNewVirtualDeviceFromVID?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115
	 *
	 * @param parameters
	 * @return
	 */
	protected Response createNewVirtualDeviceFromVID(String userID, Map<String, List<String>> parameters) {
		String accessToken = getParamValue(parameters, "accessToken");
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		String deviceType = getParamValue(parameters, "deviceType");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		if (deviceType == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed deviceType");
		}
		try {
			Device device = Device.createNewVirtualDeviceFromVID(accessToken, vendorThingID, deviceType);
			if (device == null) {
				return getErrResp(Response.Status.INTERNAL_ERROR, CODE_FAILED, "Create failed");
			}
			JSONObject json = new JSONObject();
			json.put("code", CODE_OK);
			json.put("vendorThingID", device.vendorThingID);
			json.put("globalThingID", device.globalThingID);
			return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
		} catch (IOException e) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_IO_EXCEPTION, e.getMessage());
		} catch (VendorThingIDExistException ex) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_VID_EXIST, vendorThingID + " is already existed");
		}
	}

	/**
	 * http://localhost:8129/listDevices?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63
	 *
	 * @param userID
	 * @param parameters
	 * @return
	 */

	protected Response listDevices(String userID, Map<String, List<String>> parameters) {
		String accessToken = getParamValue(parameters, "accessToken");
		try {
			List<Device> devices = Device.listDevices(accessToken, true);
			HashMap<String, Device> deviceMap = userMap.get(userID);
			JSONArray arr = new JSONArray();
			for (Device device : devices) {
				JSONObject item = new JSONObject();
				item.put("vendorThingID", device.vendorThingID);
				item.put("globalThingID", device.globalThingID);
				if (deviceMap != null && deviceMap.containsKey(device.vendorThingID)) {
					device = deviceMap.get(device.vendorThingID);
					item.put("isRunning", true);
					item.put("messageQueueSize", device.messageQueueSize);
					item.put("startTime", device.startTime);
					item.put("uploadStatePeriod", device.uploadStatePeriod);
					item.put("uploadStateOnChanged", device.uploadStateOnChanged);
					item.put("genRandomStatePeriod", device.genRandomStatePeriod);
				} else {
					item.put("isRunning", false);
					item.put("messageQueueSize", Constants.DEFAULT_MSG_QUEUE_SIZE);
				}
				item.put("type", device.getDeviceType());
				arr.put(item);
			}
			JSONObject json = new JSONObject();
			json.put("code", CODE_OK);
			json.put("devices", arr);
			return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
		} catch (IOException e) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_IO_EXCEPTION, e.getMessage());
		}
	}

	/**
	 * http://localhost:8129/startDevice?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115
	 *
	 * @param userID
	 * @param parameters
	 * @return
	 */

	protected Response startDevice(String userID, Map<String, List<String>> parameters) {
		String accessToken = getParamValue(parameters, "accessToken");
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap != null && deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.OK, CODE_ALREADY_START, "Already start");
		}
		try {
			List<Device> devices = Device.listDevices(accessToken, true);
			Device device = null;
			for (Device d : devices) {
				if (vendorThingID.equals(d.vendorThingID)) {
					device = d;
					break;
				}
			}
			if (device == null) {
				return getErrResp(Response.Status.BAD_REQUEST, CODE_FAILED, "You do not own the device");
			}
			device.start();
			if (deviceMap == null) {
				deviceMap = new HashMap<>();
				userMap.put(userID, deviceMap);
			}
			deviceMap.put(device.vendorThingID, device);
			return getEmptyOKResp();
		} catch (IOException e) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_IO_EXCEPTION, e.getMessage());
		}
	}

	/**
	 * http://localhost:8129/stopDevice?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115
	 *
	 * @param userID
	 * @param parameters
	 * @return
	 */

	protected Response stopDevice(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		device.stop();
		deviceMap.remove(vendorThingID);
		return getEmptyOKResp();
	}

	/**
	 * http://localhost:8129/deleteDevice?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115
	 *
	 * @param userID
	 * @param parameters
	 * @return
	 */

	protected Response deleteDevice(String userID, Map<String, List<String>> parameters) {
		String accessToken = getParamValue(parameters, "accessToken");
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}

		try {
			HashMap<String, Device> deviceMap = userMap.get(userID);
			if (deviceMap != null && deviceMap.containsKey(vendorThingID)) {
				Device device = deviceMap.remove(vendorThingID);
				device.delete();
			} else {
				List<Device> devices = Device.listDevices(accessToken, true);
				Device device = null;
				for (Device d : devices) {
					if (vendorThingID.equals(d.vendorThingID)) {
						device = d;
						break;
					}
				}
				if (device == null) {
					return getErrResp(Response.Status.BAD_REQUEST, CODE_FAILED, "You do not own the device");
				}
				device.delete();
			}
			return getEmptyOKResp();
		} catch (IOException e) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_IO_EXCEPTION, e.getMessage());
		}
	}


	/**
	 * http://localhost:8129/getMessageQueue?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115
	 *
	 * @param userID
	 * @param parameters
	 * @return
	 */
	protected Response getMessageQueue(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		JSONArray messages = new JSONArray();
		synchronized (device.messageQueue) {
			for (JSONObject item : device.messageQueue) {
				messages.put(item);
			}
		}
		JSONObject json = new JSONObject();
		json.put("code", CODE_OK);
		json.put("messages", messages);
		return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
	}


	/**
	 * http://localhost:8129/getStates?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115
	 *
	 * @param userID
	 * @param parameters
	 * @return
	 */
	protected Response getStates(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		JSONObject json = device.getStates();
		json.put("code", CODE_OK);
		return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
	}

	protected Response setUploadStatePeriod(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		String periodStr = getParamValue(parameters, "period");
		int period = -1;
		try {
			period = Integer.valueOf(periodStr);
		} catch (Exception e) {
			period = -1;
		}
		if (period < 0) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed period, or the data format is wrong");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		device.setUploadStatePeriod(period);
		return getEmptyOKResp();
	}

	protected Response setUploadStateOnChanged(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		String valueStr = getParamValue(parameters, "value");
		int value = -1;
		try {
			value = Integer.valueOf(valueStr);
		} catch (Exception e) {
			value = -1;
		}
		if (value != 0 && value != 1) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed value, or the data format is wrong (must be 0 or 1)");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		device.setUploadStateOnChanged(value == 1);
		return getEmptyOKResp();
	}

	protected Response setMessageQueueSize(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		String valueStr = getParamValue(parameters, "value");
		int value = -1;
		try {
			value = Integer.valueOf(valueStr);
		} catch (Exception e) {
			value = -1;
		}
		if (value <= 0) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed value, or the data format is wrong");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		device.messageQueueSize = value;
		return getEmptyOKResp();
	}

	protected Response setGenRandomStatePeriod(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		String periodStr = getParamValue(parameters, "period");
		int period = -1;
		try {
			period = Integer.valueOf(periodStr);
		} catch (Exception e) {
			period = -1;
		}
		if (period < 0) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed period, or the data format is wrong");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		device.setGenRandomStatePeriod(period);
		return getEmptyOKResp();
	}


	/**
	 * http://localhost:8129/setState?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115&field=Power&value=1
	 *
	 * @param userID
	 * @param parameters
	 * @return
	 */
	protected Response setState(String userID, Map<String, List<String>> parameters) {
		String vendorThingID = getParamValue(parameters, "vendorThingID");
		if (vendorThingID == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
		}
		List<String> field = parameters.get("field");
		if (field == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed field");
		}
		List<String> value = parameters.get("value");
		if (value == null) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed value");
		}
		if (field.size() != value.size()) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Field counts and value counts must be the same");
		}
		HashMap<String, Device> deviceMap = userMap.get(userID);
		if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
			return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
		}
		Device device = deviceMap.get(vendorThingID);
		if (device.setStates(field, value)) {
			return getEmptyOKResp();
		} else {
			return getErrResp(Response.Status.NOT_IMPLEMENTED, CODE_NOT_IMPL, "Not all the state is supported, or some value is not accepted");
		}
	}

	protected Response updateSchemas(String userID, Map<String, List<String>> parameters) {
		String accessToken = getParamValue(parameters, "accessToken");
		Device.loadSchemas();
		try {
			DeviceUtil.updateSchemas(accessToken);
		} catch (RuntimeException e) {
			return getErrResp(Response.Status.BAD_REQUEST, CODE_FAILED, e.getMessage());
		}
		return getEmptyOKResp();
	}
}
