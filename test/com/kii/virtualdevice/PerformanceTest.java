package com.kii.virtualdevice;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Evan on 2016/11/10.
 */
public class PerformanceTest {


    String userToken = null;
    String userID = null;
    OkHttpClient client = null;

    class TimeStruct {
        long startTime;
        long sentTime;
        long receivedTime;
    }

    @Before
    public void setUp() throws Exception {
        client = new OkHttpClient();
        JSONObject user = KiiUtils.loginKiiCloud("evan", "123456");
        userToken = user.optString("access_token");
        userID = user.optString("id");
        LogUtil.debug("Kii User token:" + userToken);
        Config.enableLOGD = false;
    }

    @Test
    public void testParallelSendCommand() throws IOException {
        Device testDevice = Device.createNewDevice(userID, userToken, "Lamp", "V1");
        testDevice.start();
        testDevice.setUploadStateOnChanged(false);
        ArrayList<TimeStruct> times = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            final int index = i;
            final TimeStruct t = new TimeStruct();
            times.add(t);
            new Thread() {
                @Override
                public void run() {
                    t.startTime = System.currentTimeMillis();
                    try {
                        sendCommand(testDevice.getThingID(), index);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    t.sentTime = System.currentTimeMillis();
                }
            }.start();
        }
        while (testDevice.messageQueue.size() < 100) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < 100; i++) {
            JSONObject jsonObject = testDevice.messageQueue.get(i);
            JSONArray actions = jsonObject.optJSONArray("actions");
            JSONObject actionItem = actions.getJSONObject(0);
            JSONArray a = actionItem.getJSONArray("LampAlias");
            JSONObject b = a.getJSONObject(0);
            int Bri = b.getInt("setBrightness");
            times.get(Bri).receivedTime = jsonObject.getLong("time");
        }
        for (int i = 0; i < 100; i++) {
            TimeStruct t = times.get(i);
            System.out.println(i + "\t" + t.startTime + "\t" + t.sentTime + "\t" + t.receivedTime);
        }
        testDevice.delete();
    }

    @Test
    public void testSerialSendCommand() throws IOException {
        Device testDevice = Device.createNewDevice(userID, userToken, "Lamp", "V1");
        testDevice.start();
        testDevice.setUploadStateOnChanged(false);

        for (int i = 0; i < 100; i++) {
            long startTime = System.currentTimeMillis();
            sendCommand(testDevice.getThingID(), i);
            long sentTime = System.currentTimeMillis();
            while (testDevice.messageQueue.size() < i + 1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            JSONObject data = testDevice.messageQueue.get(i);
            long receivedTime = data.getLong("time");
            System.out.println(i + "\t" + startTime + "\t" + sentTime + "\t" + receivedTime);
        }

        testDevice.delete();
    }

    public void sendCommand(String thingID, int Bri) throws IOException {
        String jsonStr = "{\n" +
                "    \"actions\": [\n" +
                "      {\n" +
                "        \"LampAlias\": [\n" +
                "          {\"setBrightness\" : " +
                Bri +
                "}\n" +
                "        ]\n" +
                "      }\n" +
                "    ],\n" +
                "    \"issuer\" : \"user:" + userID + "\"\n" +
                "  }";
        RequestBody body = RequestBody.create(MediaType.parse("application/vnd.kii.CommandCreationRequest+json"), jsonStr);
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/thing-if/apps/" + Config.KiiAppId + "/targets/thing:" + thingID + "/commands")
                .header("Authorization", "Bearer " + userToken)
                .post(body)
                .build();
        client.newCall(request).execute();
    }

}