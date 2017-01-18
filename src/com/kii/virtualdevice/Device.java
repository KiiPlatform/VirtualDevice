package com.kii.virtualdevice;

import okhttp3.*;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Evan on 2017/1/17.
 */
public class Device implements MqttCallback {

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


    private JSONObject deviceStates = new JSONObject();


    public Device(String vendorThingID, String userID, String userToken, String thingType, String firmwareVersion) {
        this.vendorThingID = vendorThingID;
        this.ownerID = userID;
        this.ownerToken = userToken;
        this.thingType = thingType;
        this.firmwareVersion = firmwareVersion;
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
//            uploadStates();
        } catch (JSONException e) {
            LogUtil.error(e.getMessage());
        }
    }

    public void stop() {
//        setGenRandomStatePeriod(0);
//        setUploadStatePeriod(0);
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
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {

    }


}
