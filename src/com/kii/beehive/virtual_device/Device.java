package com.kii.beehive.virtual_device;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import com.kii.beehive.virtual_device.exceptions.VendorThingIDExistException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by Evan on 2016/11/3.
 */
public class Device implements MqttCallback {

	public interface OnStatesChangedListener {
		void onStatesChanged(String fieldName);
	}

	public interface OnCommandReceivedListener {
		void onCommandReceived(String command);
	}

	Thread genRandomStateThread = null;
	Thread autoUploadStatesThread = null;
	boolean uploadStateOnChanged = true;
	int uploadStatePeriod = 0;
	int genRandomStatePeriod = 0;

	String vendorThingID = null;
	String thingID = null;
	int globalThingID = 0;

	private String accessToken = null;
	private String kiiSiteUrl = null;
	private String ownerToken = null;
	private String ownerID = null;
	private String kiiAppKey = null;
	private JSONObject mqttEndpoint = null;
	private String thingAccessToken = null;
	private OkHttpClient httpClient = new OkHttpClient();
	private MqttClient mqttClient = null;
	public int messageQueueSize = Constants.DEFAULT_MSG_QUEUE_SIZE;
	public long startTime = 0;
	ArrayList<JSONObject> messageQueue = new ArrayList<>();

	private String deviceType = null;
	private JSONObject deviceStates = new JSONObject();

	static JSONObject device_schemas = null;
	private JSONObject statesSchema = null;
	private JSONObject actionsSchema = null;

	private Random random = null;

	OnStatesChangedListener mOnStatesChangedListener = null;
	OnCommandReceivedListener mOnCommandReceivedListener = null;


	public static JSONArray SupportedDeviceTypes = null;
	public static HashMap<String, String> SupportedDeviceTypesMap = new HashMap<>();

	static {
		loadConfig();
		loadSchemas();
	}

	public Device(String vendorThingID) {
		this.vendorThingID = vendorThingID;
	}

	@Override
	public String toString() {
		return vendorThingID;
	}

	public static Device createNewVirtualDevice(String accessToken, String deviceType) throws IOException {
		Device device = null;
		while (device == null) {
			Random r = new Random();
			StringBuilder sb = new StringBuilder(Constants.VIRTUAL_DEVICE_VID_PREFIX);
			sb.append('-');
			char c = 'W';
			c += r.nextInt(4);
			sb.append(c);
			sb.append(10 + r.nextInt(90));
			sb.append('-');
			String typeCode = SupportedDeviceTypesMap.get(deviceType);
			sb.append(typeCode);
			sb.append('-');
			sb.append(100 + r.nextInt(900));

			String VID = sb.toString();

			try {
				device = createNewVirtualDeviceFromVID(accessToken, VID, deviceType);
			} catch (VendorThingIDExistException e) {
				device = null;
			}
		}

		return device;
	}

	public static Device createNewVirtualDeviceFromVID(String accessToken, String vendorThingID, String deviceType) throws IOException, VendorThingIDExistException {
		Request request = new Request.Builder()
				.url(Constants.BeehiveBaseUrl + "/onboardinghelper/" + vendorThingID)
				.header("Authorization", "Bearer " + accessToken)
				.build();
		OkHttpClient client = new OkHttpClient();
		try (Response response = client.newCall(request).execute()) {
			if (response.code() != 404) {
				throw new VendorThingIDExistException();
			}
		}
		Device device = getDeviceInstanceFromVID(accessToken, vendorThingID, deviceType);
		if (device == null) {
			return null;
		}
		JSONObject jsonData = new JSONObject();
		jsonData.put("vendorThingID", vendorThingID);
		jsonData.put("kiiAppID", Constants.KiiAppId);
		jsonData.put("type", device.getDeviceType());
		jsonData.put("schemaName", device.getDeviceType());
		jsonData.put("schemaVersion", 1);
		LogUtil.debug(jsonData.toString());
		RequestBody body = RequestBody.create(Constants.JSON, jsonData.toString());
		request = new Request.Builder()
				.url(Constants.BeehiveBaseUrl + "/things")
				.header("Content-Type", Constants.JSON.toString())
				.header("Authorization", "Bearer " + accessToken)
				.post(body)
				.build();
		try (Response response = client.newCall(request).execute()) {
			if (response.isSuccessful()) {
				String result = response.body().string();
				LogUtil.debug(result);
				JSONObject json = new JSONObject(result);
				int gtid = json.getInt("globalThingID");
				device.setGlobalThingID(gtid);
			} else {
				LogUtil.error(response.code() + response.body().string());
				throw new RuntimeException("Create VirtualDevice failed");
			}
		}

		return device;
	}

	public static Device getDeviceInstanceFromVID(String accessToken, String vendorThingID, String deviceType) {
		if (!SupportedDeviceTypesMap.containsKey(deviceType)) {
			System.err.println("Not supported device type:" + deviceType);
			return null;
		}
		Device device = new Device(vendorThingID);
		device.deviceType = deviceType;
		device.setAccessToken(accessToken);
		device.initSchema();
		return device;
	}

	public static List<Device> listDevices(String accessToken, boolean onlyVirtualDevice) throws IOException {
		ArrayList<Device> list = new ArrayList<>();
		Request request = new Request.Builder()
				.url(Constants.BeehiveBaseUrl + "/things/user/")
				.header("Authorization", "Bearer " + accessToken)
				.build();
		OkHttpClient client = new OkHttpClient();
		try (Response response = client.newCall(request).execute()) {
			if (response.isSuccessful()) {
				String result = response.body().string();
				JSONArray arr = new JSONArray(result);
				for (int i = 0; i < arr.length(); i++) {
					JSONObject item = arr.getJSONObject(i);
					String vendorThingID = item.getString("vendorThingID");
					if (onlyVirtualDevice && !vendorThingID.startsWith(Constants.VIRTUAL_DEVICE_VID_PREFIX)) {
						continue;
					}
					String deviceType = item.getString("schemaName");
					Device device = getDeviceInstanceFromVID(accessToken, vendorThingID, deviceType);
					if (device != null) {
						int gtid = item.getInt("globalThingID");
						device.setGlobalThingID(gtid);
						if (item.has("status")) {
							JSONObject states = item.getJSONObject("status");
							device.setStates(states);
						}
						list.add(device);
					}
				}
			}
		}
		LogUtil.debug("devices size:" + list.size());
		return list;
	}

	public String getVendorThingID() {
		return vendorThingID;
	}

	public void initSchema() {
		JSONObject currentSchema = device_schemas.optJSONObject(deviceType);
//        Assert.checkNonNull(currentSchema, "Cannot load schema for device: " + vendorThingID);
		JSONObject tmp = currentSchema.optJSONObject("statesSchema");
		statesSchema = tmp.optJSONObject("properties");
		actionsSchema = currentSchema.optJSONObject("actions");
		for (String s : statesSchema.keySet()) {
			JSONObject statesDetail = statesSchema.optJSONObject(s);
			JSONObject stateEnum = statesDetail.optJSONObject("enum");
			if (stateEnum != null) {
				Iterator<String> key = stateEnum.keys();
				String firstKey = key.next();
				int data = stateEnum.getInt(firstKey);
				deviceStates.put(s, data);
				continue;
			}
			if (statesDetail.has("minimum")) {
				deviceStates.put(s, statesDetail.getInt("minimum"));
				continue;
			}
			deviceStates.put(s, 0);
		}
	}

	public void loadDeviceInfo() throws IOException {
		Request request = new Request.Builder()
				.url(Constants.BeehiveBaseUrl + "/onboardinghelper/" + vendorThingID)
				.header("Authorization", "Bearer " + accessToken)
				.build();
		try (Response response = httpClient.newCall(request).execute()) {
			LogUtil.debug("Init code:" + response.code());
			if (response.isSuccessful()) {
				String result = response.body().string();
				LogUtil.debug(result);
				JSONObject json = new JSONObject(result);
				kiiAppKey = json.getString("kiiAppKey");
				ownerID = json.getString("ownerID");
				ownerToken = json.getString("ownerToken");
				kiiSiteUrl = json.getString("kiiSiteUrl");
			}
		}
	}

	public boolean onBoarding() throws IOException {
		if (kiiSiteUrl == null || kiiSiteUrl.length() == 0) {
			loadDeviceInfo();
		}
		String url = kiiSiteUrl + "/thing-if/apps/" + Constants.KiiAppId + "/onboardings";
		JSONObject jsonData = new JSONObject();
		jsonData.put("vendorThingID", vendorThingID);
		jsonData.put("thingPassword", vendorThingID);
		jsonData.put("owner", "user:" + ownerID);
		LogUtil.debug(url);
		LogUtil.debug(jsonData.toString());
		RequestBody body = RequestBody.create(
				MediaType.parse("application/vnd.kii.OnboardingWithVendorThingIDByOwner+json"),
				jsonData.toString()
		);
		Request request = new Request.Builder()
				.url(url)
				.header("accept", "*/*")
				.header("Authorization", "Bearer " + ownerToken)
				.post(body)
				.build();
		try (Response response = httpClient.newCall(request).execute()) {
			LogUtil.debug(String.valueOf(response.code()));
			if (response.isSuccessful()) {
				String result = response.body().string();
				LogUtil.debug(result);
				JSONObject jsonResult = new JSONObject(result);
				mqttEndpoint = jsonResult.getJSONObject("mqttEndpoint");
				thingAccessToken = jsonResult.getString("accessToken");
				thingID = jsonResult.getString("thingID");
				return true;
			}
		}
		return false;
	}

	public void start() throws IOException {
		if (mqttEndpoint == null) {
			onBoarding();
		}
		String topic = mqttEndpoint.getString("mqttTopic");
		String clientId = topic;

		String host = mqttEndpoint.getString("host");
		int port = mqttEndpoint.getInt("portTCP");
		String broker = "tcp://" + host + ":" + port;

		String userName = mqttEndpoint.getString("username");
		String password = mqttEndpoint.getString("password");
		try {
			mqttClient = new MqttClient(broker, clientId);
			mqttClient.setCallback(this);
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setUserName(userName);
			connOpts.setPassword(password.toCharArray());
			mqttClient.connect(connOpts);
			LogUtil.debug("Connected to Mqtt broker");
			mqttClient.subscribe(topic, Constants.QoS);
		} catch (MqttException e) {
			e.printStackTrace();
		}
		startTime = System.currentTimeMillis();

		if (!getStatesFromServer()) {
			LogUtil.debug("Need to uploadStates since no states on server");
			uploadStates();
		}
	}

	public void stop() {
		setGenRandomStatePeriod(0);
		setUploadStatePeriod(0);
		if (mqttClient != null && mqttClient.isConnected()) {
			try {
				mqttClient.disconnect();
			} catch (MqttException e) {
				e.printStackTrace();
			}
		}
		startTime = 0;
	}


	public boolean delete() throws IOException {
		if (globalThingID == 0) {
			loadDeviceInfo();
		}
		stop();
		Request request = new Request.Builder()
				.url(Constants.BeehiveBaseUrl + "/things/" + globalThingID + "/hard")
				.header("Authorization", "Bearer " + accessToken)
				.delete()
				.build();
		try (Response response = httpClient.newCall(request).execute()) {
			return response.isSuccessful();
		}
	}

	public boolean uploadStates() throws IOException {
		JSONObject states = deviceStates;
		if (thingID == null) {
			onBoarding();
		}
		if (states == null || states.length() == 0) {
			return true;
		}
		JSONObject uploadData = new JSONObject(states, JSONObject.getNames(states));
		addExtraStateInfo(uploadData);
		String url = kiiSiteUrl + "/thing-if/apps/" + Constants.KiiAppId + "/targets/THING:" + thingID + "/states";
		LogUtil.debug(url);
		LogUtil.debug(uploadData.toString());
		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"),
				uploadData.toString()
		);
		Request request = new Request.Builder()
				.url(url)
				.header("accept", "*/*")
				.header("Authorization", "Bearer " + thingAccessToken)
				.put(body)
				.build();
		try (Response response = httpClient.newCall(request).execute()) {
			LogUtil.debug(String.valueOf(response.code()));
			if (response.isSuccessful()) {
				return true;
			}
		}

		return false;
	}

	public boolean getStatesFromServer() {
		String url = kiiSiteUrl + "/thing-if/apps/" + Constants.KiiAppId + "/targets/THING:" + thingID + "/states";
		Request request = new Request.Builder()
				.url(url)
				.header("accept", "*/*")
				.header("Authorization", "Bearer " + thingAccessToken)
				.get()
				.build();
		try (Response response = httpClient.newCall(request).execute()) {
			if (response.isSuccessful()) {
				String data = response.body().string();
				LogUtil.debug("getStatesFromServer: " + data);
				JSONObject states = new JSONObject(data);
				setStates(states);
				return states.length() > 2;
			}
		} catch (Exception e) {
			LogUtil.error(e.getMessage());
		}
		return false;
	}

	/**
	 * @param period Unit: Seconds
	 */
	public void setGenRandomStatePeriod(int period) {
		this.genRandomStatePeriod = period;
		if (genRandomStateThread != null) {
			genRandomStateThread.interrupt();
			genRandomStateThread = null;
		}
		if (period > 0) {
			genRandomStatePeriod = period;
			genRandomStateThread = new Thread() {
				@Override
				public void run() {
					while (!isInterrupted()) {
						try {
							genRandomStatus();
							sleep(period * 1000);
						} catch (InterruptedException e) {
							break;
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			};
			genRandomStateThread.start();
		}
	}


	/**
	 * this function will add below two fields into thing states
	 * - date: timestamp in million seconds
	 * - target: kii thing id
	 *
	 * @param states
	 */
	private void addExtraStateInfo(JSONObject states) {
		states.put("date", System.currentTimeMillis());
		states.put("target", thingID);
	}

	public boolean isUploadStateOnChanged() {
		return uploadStateOnChanged;
	}

	public void setUploadStateOnChanged(boolean uploadStateOnChanged) {
		this.uploadStateOnChanged = uploadStateOnChanged;
	}

	public int getUploadStatePeriod() {
		return uploadStatePeriod;
	}

	/**
	 * @param uploadStatePeriod Unit: Seconds 0: Stop
	 */
	public void setUploadStatePeriod(int uploadStatePeriod) {
		this.uploadStatePeriod = uploadStatePeriod;
		if (autoUploadStatesThread != null) {
			autoUploadStatesThread.interrupt();
			autoUploadStatesThread = null;
		}
		if (uploadStatePeriod > 0) {
			autoUploadStatesThread = new Thread() {
				@Override
				public void run() {
					while (!isInterrupted()) {
						try {
							sleep(Device.this.uploadStatePeriod * 1000);
							uploadStates();
						} catch (InterruptedException e) {
							break;
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			};
			autoUploadStatesThread.start();
		}
	}

	public String getDeviceType() {
		return deviceType;
	}

	public void genRandomStatus() {
		if (random == null) {
			random = new Random();
		}
		for (String s : statesSchema.keySet()) {
			JSONObject statesDetail = statesSchema.optJSONObject(s);
			JSONObject stateEnum = statesDetail.optJSONObject("enum");
			if (stateEnum != null) {
				int pos = random.nextInt(stateEnum.length());
				String key = stateEnum.names().getString(pos);
				int data = stateEnum.getInt(key);
				deviceStates.put(s, data);
				continue;
			}
			int min, max;
			min = statesDetail.optInt("minimum", 0);
			max = statesDetail.optInt("maximum", 0);
			if (max != min) {
				int data = random.nextInt(max - min) + min;
				deviceStates.put(s, data);
			}
		}
		if (isUploadStateOnChanged()) {
			try {
				uploadStates();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public JSONObject getStates() {
		return deviceStates;
	}

	public JSONObject handleAction(String actionName, JSONObject parameters) {
		JSONObject actionDetail = actionsSchema.optJSONObject(actionName);
		if (actionDetail == null) {
			return getUnimplementActionResult();
		}
		JSONObject nodeIn = actionDetail.optJSONObject("in");
		JSONObject properties = nodeIn.optJSONObject("properties");
		if (properties != null && properties.names().length() > 0) {
			String stateKey = properties.names().optString(0);
			deviceStates.put(stateKey, parameters.get(stateKey));
		}
		return buildActionResult(true, "", parameters);
	}

	public boolean setStates(List<String> stateNames, List<String> values) {
		JSONObject changedState = new JSONObject();
		for (int i = 0; i < stateNames.size(); i++) {
			String stateName = stateNames.get(i);
			String value = values.get(i);
			if (deviceStates.has(stateName)) {
				int checkData = 0;
				JSONObject statesDetail = statesSchema.optJSONObject(stateName);
				String type = statesDetail.getString("type");
				if ("string".equalsIgnoreCase(type)) {
					changedState.put(stateName, value);
					continue;
				}
				if ("TRUE".equalsIgnoreCase(value)) {
					changedState.put(stateName, true);
					checkData = 1;
				} else if ("FALSE".equalsIgnoreCase(value)) {
					changedState.put(stateName, false);
					checkData = 0;
				}
				try {
					if (value.contains(".")) {
						changedState.put(stateName, Double.parseDouble(value));
						checkData = (int) Double.parseDouble(value);
					} else {
						changedState.put(stateName, Integer.parseInt(value));
						checkData = Integer.parseInt(value);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				JSONObject stateEnum = statesDetail.optJSONObject("enum");
				if (stateEnum != null) {
					boolean hasValue = false;
					String[] names = JSONObject.getNames(stateEnum);
					for (String s : names) {
						int enumValue = stateEnum.getInt(s);
						if (checkData == enumValue) {
							hasValue = true;
							break;
						}
					}
					if (!hasValue) {
						changedState.remove(stateName);
						continue;
					}
				}
				int min, max;
				min = statesDetail.optInt("minimum", 0);
				max = statesDetail.optInt("maximum", 0);
				if (max != min) {
					if (checkData < min || checkData > max) {
						changedState.remove(stateName);
						continue;
					}
				}
			}
		}
		for (String name : changedState.keySet()) {
			deviceStates.put(name, changedState.get(name));
			if (mOnStatesChangedListener != null) {
				mOnStatesChangedListener.onStatesChanged(name);
			}
		}
		if (isUploadStateOnChanged()) {
			try {
				uploadStates();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (changedState.length() == stateNames.size()) {
			return true;
		}
		return false;
	}

	private void setStates(JSONObject states) {
		for (String name : states.keySet()) {
			if (deviceStates.has(name)) {
				deviceStates.put(name, states.get(name));
				if (mOnStatesChangedListener != null) {
					mOnStatesChangedListener.onStatesChanged(name);
				}
			}
		}
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public JSONObject buildActionResult(boolean succ, String errorMessage, JSONObject customData) {
		JSONObject result = new JSONObject();
		result.put("succeeded", succ);
		result.put("errorMessage", errorMessage);
		result.put("data", customData);
		return result;
	}

	protected JSONObject getUnimplementActionResult() {
		return buildActionResult(false, "Unimplemented action", null);
	}

	public void setGlobalThingID(int globalThingID) {
		this.globalThingID = globalThingID;
	}

	public int getGlobalThingID() {
		return this.globalThingID;
	}

	@Override
	public void connectionLost(Throwable throwable) {
		LogUtil.debug("connectionLost: " + throwable.getMessage());
	}

	@Override
	public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
		LogUtil.debug("messageArrived topic:" + topic + " MqttMessage: " + mqttMessage);
		String msgData = new String(mqttMessage.getPayload(), "UTF-8");
		if (mOnCommandReceivedListener != null) {
			mOnCommandReceivedListener.onCommandReceived(msgData);
		}
		JSONObject jsonObject = new JSONObject(msgData);
		jsonObject.put("time", System.currentTimeMillis());
		synchronized (messageQueue) {
			while (messageQueue.size() >= messageQueueSize) {
				messageQueue.remove(0);
			}
			messageQueue.add(jsonObject);
		}
		JSONArray actions = jsonObject.optJSONArray("actions");
		final String commandID = jsonObject.optString("commandID");
		if (actions != null) {
			JSONArray actionResults = new JSONArray();
			for (int i = 0; i < actions.length(); i++) {
				JSONObject actionItem = actions.getJSONObject(i);
				Iterator<String> keyIterator = actionItem.keys();
				if (keyIterator.hasNext()) {
					String actionName = keyIterator.next();
					JSONObject actionParameters = actionItem.getJSONObject(actionName);
					JSONObject result = handleAction(actionName, actionParameters);
					JSONObject actionResultItem = new JSONObject();
					actionResultItem.put(actionName, result);
					actionResults.put(actionResultItem);
				}
			}
			if (mOnStatesChangedListener != null) {
				mOnStatesChangedListener.onStatesChanged(null);
			}
			final JSONObject jsonBody = new JSONObject();
			jsonBody.put("actionResults", actionResults);
			new Thread() {
				@Override
				public void run() {
					String url = kiiSiteUrl + "/thing-if/apps/" + Constants.KiiAppId + "/targets/THING:" + thingID
							+ "/commands/" + commandID + "/action-results";
					LogUtil.debug(url);
					LogUtil.debug(jsonBody.toString());
					RequestBody body = RequestBody.create(
							MediaType.parse("application/json"),
							jsonBody.toString()
					);
					Request request = new Request.Builder()
							.url(url)
							.header("accept", "*/*")
							.header("Authorization", "Bearer " + thingAccessToken)
							.put(body)
							.build();
					try (Response response = httpClient.newCall(request).execute()) {
						LogUtil.debug("Send action results:" + String.valueOf(response.code()));
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (isUploadStateOnChanged()) {
						try {
							uploadStates();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}.start();
		}
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
		LogUtil.debug("deliveryComplete:" + iMqttDeliveryToken);
	}

	public static void loadSchemas() {
		JSONObject result = new JSONObject();
		for (String s : SupportedDeviceTypesMap.keySet()) {
			File file = new File(s + ".json");
			String fileData = null;
			try {
				fileData = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
			} catch (Exception e) {
				System.err.println(e);
				return;
			}
			try {
				JSONObject json = new JSONObject(fileData);
				JSONObject content = json.optJSONObject("content");
				if (content == null) {
					return;
				}
				result.put(s, content);
			} catch (Exception e) {
				LogUtil.error(s + "\n" + fileData + "\n" + e.getMessage());
				return;
			}
		}
		device_schemas = result;
	}

	public static void loadConfig() {
		File file = new File("config.json");
		System.out.println(file.getAbsolutePath());
		String fileData = null;
		try {
			fileData = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
		} catch (Exception e) {
			LogUtil.error(e.getMessage());
			throw new RuntimeException("Cannot read Config json file");
		}
		try {
			JSONObject json = new JSONObject(fileData);
			Constants.KiiAppId = json.getString("kiiAppId");
			Constants.BeehiveBaseUrl = json.getString("beehiveBaseUrl");
			SupportedDeviceTypes = json.getJSONArray("supportTypes");
			for (int i = 0; i < SupportedDeviceTypes.length(); i++) {
				JSONObject item = SupportedDeviceTypes.getJSONObject(i);
				SupportedDeviceTypesMap.put(item.getString("type"), item.getString("code"));
			}
		} catch (Exception e) {
			LogUtil.error(e.getMessage());
			throw new RuntimeException("Cannot load Config json file");
		}
	}

	public static boolean initedSchemas() {
		return device_schemas != null;
	}

	public OnStatesChangedListener getOnStatesChangedListener() {
		return mOnStatesChangedListener;
	}

	public void setOnStatesChangedListener(OnStatesChangedListener mOnStatesChangedListener) {
		this.mOnStatesChangedListener = mOnStatesChangedListener;
	}

	public OnCommandReceivedListener getOnCommandReceivedListener() {
		return mOnCommandReceivedListener;
	}

	public void setOnCommandReceivedListener(OnCommandReceivedListener mOnCommandReceivedListener) {
		this.mOnCommandReceivedListener = mOnCommandReceivedListener;
	}
}
