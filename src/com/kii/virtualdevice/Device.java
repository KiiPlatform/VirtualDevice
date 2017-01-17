package com.kii.virtualdevice;

import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Created by Evan on 2017/1/17.
 */
public class Device {

    private JSONObject mqttEndpoint = null;
    private String thingAccessToken = null;
    private String thingID = null;
    private String ownerToken = null;
    private String ownerID = null;
    private String vendorThingID = null;
    private String thingType = null;
    private String firmwareVersion = null;

    private int messageQueueSize = Config.DEFAULT_MSG_QUEUE_SIZE;
    ArrayList<JSONObject> messageQueue = new ArrayList<>();
    OkHttpClient client = new OkHttpClient();


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

        if (device.thingID != null && device.thingID.length() > 0) {
            return device;
        }
        return null;
    }

    public static Device createNewDevice(String userID, String userToken, String thingType, String firmwareVersion) {
        String vendorThingID = UUID.randomUUID().toString();
        return createNewDevice(vendorThingID, userID, userToken, thingType, firmwareVersion);
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
                .post(body)
                .build();
        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String result = response.body().string();
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
}
