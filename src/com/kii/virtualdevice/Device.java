package com.kii.virtualdevice;

import okhttp3.*;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by Evan on 2017/1/17.
 */
public class Device implements MqttCallback {

    public interface OnStatesChangedListener {
        void onStatesChanged();
    }

    public interface OnCommandReceivedListener {
        void onCommandReceived(String command);
    }

    private JSONObject mqttEndpoint = null;
    private String thingAccessToken = null;

    private String thingID = null;
    private String ownerToken = null;
    private String ownerID = null;
    private String vendorThingID = null;
    private String thingType = null;
    private String firmwareVersion = null;

    private long startTime = 0;

    private int messageQueueSize = Config.DEFAULT_MSG_QUEUE_SIZE;
    ArrayList<JSONObject> messageQueue = new ArrayList<>();
    OkHttpClient client = new OkHttpClient();
    private MqttClient mqttClient = null;

    Thread genRandomStateThread = null;
    Thread autoUploadStatesThread = null;
    boolean uploadStateOnChanged = true;
    int uploadStatePeriod = 0;
    int genRandomStatePeriod = 0;


    private HashMap<String, JSONObject> deviceAlias = new HashMap<>();
    private HashMap<String, String> aliasJS = new HashMap<>();
    OnStatesChangedListener mOnStatesChangedListener = null;
    OnCommandReceivedListener mOnCommandReceivedListener = null;

    private Random random = new Random();

    public Device(String vendorThingID, String userID, String userToken, String thingType, String firmwareVersion) {
        this.vendorThingID = vendorThingID;
        this.ownerID = userID;
        this.ownerToken = userToken;
        this.thingType = thingType;
        this.firmwareVersion = firmwareVersion;
        loadTraits();
    }

    public static Device createNewDevice(String vendorThingID, String userID, String userToken, String thingType, String firmwareVersion) {
        Device device = new Device(vendorThingID, userID, userToken, thingType, firmwareVersion);
        device.onboarding();

        if (device.thingID == null || device.thingID.length() == 0) {
            return null;
        }

        device.storeThingToBucket();
        return device;
    }

    private boolean storeThingToBucket() {
        JSONObject json = new JSONObject();
        json.put("vendorThingID", vendorThingID);
        json.put("thingID", thingID);
        json.put("thingType", thingType);
        json.put("firmwareVersion", firmwareVersion);
        RequestBody body = RequestBody.create(MediaType.parse("application/vnd." + Config.KiiAppId + ".mydata+json"), json.toString());
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/api/apps/" + Config.KiiAppId + "/users/me/buckets/" + Config.VIRTUAL_DEVICE_BUCKET + "/objects/" + thingID)
                .header("Authorization", "Bearer " + ownerToken)
                .put(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String result = response.body().string();
                LogUtil.debug("storeThingToBucket:\n" + result);
                return true;
            }
        } catch (IOException e) {
            LogUtil.error(e.getMessage());
        }
        return false;
    }

    public static Device createNewDevice(String userID, String userToken, String thingType, String firmwareVersion) {
        String vendorThingID = "VirtualDevice_" + UUID.randomUUID().toString();
        return createNewDevice(vendorThingID, userID, userToken, thingType, firmwareVersion);
    }

    public static List<Device> listDevices(String userToken) {
        ArrayList<Device> devicesList = new ArrayList<>();
        String paginationKey = null;
        OkHttpClient client = new OkHttpClient();
        do {
            JSONObject json = new JSONObject();
            JSONObject clause = new JSONObject();
            clause.put("type", "all");
            JSONObject bucketQuery = new JSONObject();
            bucketQuery.put("clause", clause);
            json.put("bucketQuery", bucketQuery);
            if (paginationKey != null) {
                json.put("paginationKey", paginationKey);
            }
            paginationKey = null;
            RequestBody body = RequestBody.create(MediaType.parse("application/vnd.kii.QueryRequest+json"), json.toString());
            Request request = new Request.Builder()
                    .url(Config.KiiSiteUrl + "/api/apps/" + Config.KiiAppId + "/users/me/buckets/" + Config.VIRTUAL_DEVICE_BUCKET + "/query")
                    .header("Authorization", "Bearer " + userToken)
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    LogUtil.debug("listDevices\n" + result);
                    JSONObject jsonObject = new JSONObject(result);
                    JSONArray results = jsonObject.optJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject item = results.optJSONObject(i);
                        String vendorThingID = item.getString("vendorThingID");
                        String thingID = item.getString("thingID");
                        String thingType = item.getString("thingType");
                        String firmwareVersion = item.getString("firmwareVersion");
                        String ownerID = item.getString("_owner");
                        Device device = new Device(vendorThingID, ownerID, userToken, thingType, firmwareVersion);
                        device.thingID = thingID;
                        devicesList.add(device);
                    }
                    paginationKey = jsonObject.optString("nextPaginationKey");
                }
            } catch (IOException e) {
                LogUtil.error(e.getMessage());
            }
        } while (paginationKey != null);
        return devicesList;
    }

    public void onboarding() {
        JSONObject json = new JSONObject();
        json.put("vendorThingID", vendorThingID);
        json.put("thingPassword", Config.THING_PASSWORD);
        json.put("thingType", thingType);
        json.put("owner", "user:" + ownerID);
        json.put("firmwareVersion", firmwareVersion);
        RequestBody body = RequestBody.create(MediaType.parse("application/vnd.kii.OnboardingWithVendorThingIDByOwner+json"), json.toString());
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/thing-if/apps/" + Config.KiiAppId + "/onboardings")
                .header("X-Kii-AppID", Config.KiiAppId)
                .header("X-Kii-AppKey", Config.KiiAppKey)
                .header("Authorization", "Bearer " + ownerToken)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String result = response.body().string();
                LogUtil.debug("onboarding\n" + result);
                JSONObject jsonObject = null;
                jsonObject = new JSONObject(result);

                mqttEndpoint = jsonObject.optJSONObject("mqttEndpoint");
                thingID = jsonObject.optString("thingID");
                thingAccessToken = jsonObject.optString("accessToken");
            }
        } catch (IOException e) {
            LogUtil.error(e.getMessage());
        }
    }


    public String getThingID() {
        return thingID;
    }

    public String getVendorThingID() {
        return vendorThingID;
    }

    public String getThingType() {
        return thingType;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public int getMessageQueueSize() {
        return messageQueueSize;
    }

    public void setMessageQueueSize(int messageQueueSize) {
        this.messageQueueSize = messageQueueSize;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void loadTraits() {
        for (int i = 0; i < Config.SupportedTypes.length(); i++) {
            JSONObject item = Config.SupportedTypes.optJSONObject(i);
            String thingType = item.optString("thingType");
            String firmwareVersion = item.optString("firmwareVersion");
            if (this.thingType.equals(thingType) && this.firmwareVersion.equals(firmwareVersion)) {
                JSONArray alias = item.optJSONArray("alias");
                for (int j = 0; j < alias.length(); j++) {
                    JSONObject aliasItem = alias.optJSONObject(j);
                    String name = aliasItem.optString("name");
                    String trait = aliasItem.optString("trait");
                    File file = new File(trait + ".json");
                    String fileData = null;
                    try {
                        fileData = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
                        JSONObject traitDetail = new JSONObject(fileData);
                        aliasItem.put("traitDetail", traitDetail);
                    } catch (Exception e) {
                        LogUtil.error(e.getMessage());
                        throw new RuntimeException("Cannot read trait file:" + trait + ".json");
                    }
                    file = new File(name + ".js");
                    try {
                        if (file.exists()) {
                            fileData = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
                            if (fileData != null) {
                                aliasJS.put(name, fileData);
                            }
                        }
                    } catch (Exception e) {
                        LogUtil.error(e.getMessage());
                    }
                    deviceAlias.put(name, aliasItem);
                }
                break;
            }
        }
        genRandomStatus(true);
    }


    public void start() throws IOException {
        if (mqttEndpoint == null) {
            onboarding();
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
            mqttClient.subscribe(topic, Config.QoS);
        } catch (MqttException e) {
            e.printStackTrace();
        }
        startTime = System.currentTimeMillis();
        getStatesFromServer();
        uploadStates();
    }

    public void stop() {
        setGenRandomStatePeriod(0);
        setUploadStatePeriod(0);
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                LogUtil.error(e.getMessage());
            }
        }
        startTime = 0;
    }

    public boolean delete() throws IOException {
        stop();
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/api/apps/" + Config.KiiAppId + "/things/" + thingID)
                .header("Authorization", "Bearer " + ownerToken)
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            LogUtil.debug("Delete thing:" + response.code());
        }

        request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/api/apps/" + Config.KiiAppId + "/users/me/buckets/" + Config.VIRTUAL_DEVICE_BUCKET + "/objects/" + thingID)
                .header("Authorization", "Bearer " + ownerToken)
                .header("If-Match", "1")
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            LogUtil.debug("Delete thing from bucket:" + response.code());
            return response.isSuccessful();
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        LogUtil.debug("connectionLost: " + throwable.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        LogUtil.debug("deliveryComplete:" + iMqttDeliveryToken);
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
        JSONArray actionResults = new JSONArray();
        for (int i = 0; i < actions.length(); i++) {
            JSONObject actionItem = actions.getJSONObject(i);
            Iterator<String> keyIterator = actionItem.keys();
            if (keyIterator.hasNext()) {
                String aliasName = keyIterator.next();
                JSONObject actionResultItem = new JSONObject();
                actionResults.put(actionResultItem);
                JSONArray aliasActionResults = new JSONArray();
                actionResultItem.put(aliasName, aliasActionResults);
                JSONArray aliasActions = actionItem.getJSONArray(aliasName);

                String JS = aliasJS.get(aliasName);
                JSONObject aliasItem = deviceAlias.get(aliasName);
                for (int j = 0; j < aliasActions.length(); j++) {
                    JSONObject actionDetail = aliasActions.getJSONObject(j);
                    String actionName = (String) actionDetail.keys().next();
                    if (JS != null) {
                        try {
                            JSONObject jsInput = new JSONObject();
                            jsInput.put("action", actionDetail);
                            jsInput.put("states", aliasItem.optJSONObject("states"));
                            JSONObject states = JSHandler.interpret(JS, "run", jsInput);
                            aliasItem.put("states", states);
                        } catch (Exception e) {
                            LogUtil.error(e.getMessage());
                        }
                        JSONObject resultItem = new JSONObject();
                        JSONObject resultItemDetail = new JSONObject();
                        resultItemDetail.put("succeeded", true);
                        resultItem.put(actionName, resultItemDetail);
                        aliasActionResults.put(resultItem);
                    }
                }
            }
        }

        if (mOnStatesChangedListener != null) {
            mOnStatesChangedListener.onStatesChanged();
        }

        final JSONObject jsonBody = new JSONObject();
        jsonBody.put("actionResults", actionResults);
        new Thread(() -> {
            String url = Config.KiiSiteUrl + "/thing-if/apps/" + Config.KiiAppId + "/targets/THING:" + thingID
                    + "/commands/" + commandID + "/action-results";
            LogUtil.debug(jsonBody.toString());
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/vnd.kii.CommandResultsUpdateRequest+json"),
                    jsonBody.toString()
            );
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + thingAccessToken)
                    .put(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
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
        }).start();
    }


    public boolean uploadStates() throws IOException {
        JSONObject uploadData = getStates();
        String url = Config.KiiSiteUrl + "/thing-if/apps/" + Config.KiiAppId + "/targets/THING:" + thingID + "/states";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/vnd.kii.MultipleTraitState+json"),
                uploadData.toString()
        );
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + thingAccessToken)
                .put(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            LogUtil.debug("uploadStates:" + uploadData.toString() + " result:" + String.valueOf(response.code()));
            if (response.isSuccessful()) {
                return true;
            }
        }
        return false;
    }


    public void getStatesFromServer() {
        String url = Config.KiiSiteUrl + "/thing-if/apps/" + Config.KiiAppId + "/targets/THING:" + thingID + "/states";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + thingAccessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String result = response.body().string();
                LogUtil.debug(result);
                JSONObject jsonObject = null;
                jsonObject = new JSONObject(result);
                JSONArray names = jsonObject.names();
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i);
                    JSONObject states = jsonObject.optJSONObject(name);
                    JSONObject alias = deviceAlias.get(name);
                    if (alias != null && states.length() > 0) {
                        alias.put("states", states);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
        }
    }

    /**
     * @param period Unit: Seconds
     */
    public void setGenRandomStatePeriod(int period) {
        if ((mqttClient == null || !mqttClient.isConnected()) && period != 0) {
            throw new RuntimeException("setGenRandomStatePeriod after start device");
        }
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
                            genRandomStatus(false);
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

    public void genRandomStatus(boolean isInitData) {
        for (JSONObject alias : deviceAlias.values()) {
            JSONObject traitDetail = alias.optJSONObject("traitDetail");
            JSONObject states = new JSONObject();
            JSONArray statesArr = traitDetail.optJSONArray("states");
            for (int i = 0; i < statesArr.length(); i++) {
                JSONObject stateItem = statesArr.optJSONObject(i);
                Iterator<String> key = stateItem.keys();
                String firstKey = key.next();
                JSONObject keyData = stateItem.optJSONObject(firstKey);
                JSONObject payloadSchema = keyData.optJSONObject("payloadSchema");
                String type = payloadSchema.optString("type");
                switch (type) {
                    case "integer": {
                        int max = payloadSchema.optInt("maximum", 0);
                        int min = payloadSchema.optInt("minimum", 0);
                        states.put(firstKey, random.nextInt(max - min) + min);
                    }
                    break;
                    case "boolean":
                        states.put(firstKey, random.nextInt() % 2 == 0);
                        break;
                }
                alias.put("states", states);
            }
        }
        if (!isInitData) {
            if (mOnStatesChangedListener != null) {
                mOnStatesChangedListener.onStatesChanged();
            }
            if (isUploadStateOnChanged()) {
                try {
                    uploadStates();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public JSONObject getStates() {
        JSONObject results = new JSONObject();
        for (JSONObject alias : deviceAlias.values()) {
            String name = alias.optString("name");
            JSONObject states = alias.optJSONObject("states");
            results.put(name, states);
        }
        return results;
    }


    public boolean setStates(String alias, List<String> stateNames, List<String> values) {
        JSONObject changedState = new JSONObject();
        JSONObject aliasItem = deviceAlias.get(alias);
        if (aliasItem == null) {
            return false;
        }
        JSONObject deviceStates = aliasItem.getJSONObject("states");
        for (int i = 0; i < stateNames.size(); i++) {
            String stateName = stateNames.get(i);
            String value = values.get(i);
            if (deviceStates.has(stateName)) {
                int checkData = 0;
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
            }
        }
        for (String name : changedState.keySet()) {
            deviceStates.put(name, changedState.get(name));
        }
        if (mOnStatesChangedListener != null) {
            mOnStatesChangedListener.onStatesChanged();
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

    @Override
    public String toString() {
        return vendorThingID;
    }
}
