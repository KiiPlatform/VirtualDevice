package com.kii.virtualdevice;

import okhttp3.*;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONArray;
import org.json.JSONException;
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
        try {
            json.put("vendorThingID", vendorThingID);
            json.put("thingID", thingID);
            json.put("thingType", thingType);
            json.put("firmwareVersion", firmwareVersion);
        } catch (JSONException e) {
        }
        RequestBody body = RequestBody.create(MediaType.parse("application/vnd." + Config.KiiAppId + ".mydata+json"), json.toString());
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/api/apps/" + Config.KiiAppId + "/users/me/buckets/" + Config.VIRTUAL_DEVICE_BUCKET + "/objects/" + thingID)
                .header("Authorization", "Bearer " + ownerToken)
                .put(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String result = response.body().string();
                LogUtil.debug(result);
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

    public static List<Device> listMyDevices(String userToken) {
        ArrayList<Device> devicesList = new ArrayList<>();
        String paginationKey = null;
        OkHttpClient client = new OkHttpClient();
        do {
            JSONObject json = new JSONObject();
            try {
                JSONObject clause = new JSONObject();
                clause.put("type", "all");
                JSONObject bucketQuery = new JSONObject();
                bucketQuery.put("clause", clause);
                json.put("bucketQuery", bucketQuery);
                if (paginationKey != null) {
                    json.put("paginationKey", paginationKey);
                }
            } catch (JSONException e) {
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
                    LogUtil.debug(result);
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
                        devicesList.add(device);
                    }
                    paginationKey = jsonObject.optString("nextPaginationKey");
                }
            } catch (IOException e) {
                LogUtil.error(e.getMessage());
            } catch (JSONException e) {
                LogUtil.error(e.getMessage());
            }
        } while (paginationKey != null);
        return devicesList;
    }

    public void onboarding() {
        JSONObject json = new JSONObject();
        try {
            json.put("vendorThingID", vendorThingID);
            json.put("thingPassword", Config.THING_PASSWORD);
            json.put("thingType", thingType);
            json.put("owner", "user:" + ownerID);
            json.put("firmwareVersion", firmwareVersion);
        } catch (JSONException e) {
        }
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
                LogUtil.debug(result);
                JSONObject jsonObject = null;
                jsonObject = new JSONObject(result);

                mqttEndpoint = jsonObject.optJSONObject("mqttEndpoint");
                thingID = jsonObject.optString("thingID");
                thingAccessToken = jsonObject.optString("accessToken");
            }
        } catch (IOException e) {
            LogUtil.error(e.getMessage());
        } catch (JSONException e) {
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
                    deviceAlias.put(name, aliasItem);
                }
                break;
            }
        }
        genRandomStatus();
    }


    public void start() throws IOException {
        if (mqttEndpoint == null) {
            onboarding();
        }
        try {
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
        } catch (JSONException e) {
            LogUtil.error(e.getMessage());
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
        stop();
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/apps/" + Config.KiiAppId + "/things/" + thingID)
                .header("Authorization", "Bearer " + thingAccessToken)
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
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
    }


    public boolean uploadStates() throws IOException {
        JSONObject uploadData = new JSONObject();
        try {
            for (JSONObject alias : deviceAlias.values()) {
                String name = alias.optString("name");
                JSONObject states = alias.optJSONObject("states");
                uploadData.put(name, states);
            }
        } catch (JSONException e) {
            LogUtil.error(e.getMessage());
        }
        String url = Config.KiiSiteUrl + "/thing-if/apps/" + Config.KiiAppId + "/targets/THING:" + thingID + "/states";
        LogUtil.debug(uploadData.toString());
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
            LogUtil.debug(String.valueOf(response.code()));
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

    public void genRandomStatus() {
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
                try {
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
                } catch (JSONException e) {
                    LogUtil.error(e.getMessage());
                }
            }
        }
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
