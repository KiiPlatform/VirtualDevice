package com.kii.beehive.virtual_device;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * Created by Evan on 2016/11/10.
 */
public class PerformanceTest {


    String userToken = null;
    OkHttpClient client = null;

    class TimeStruct {
        long startTime;
        long sentTime;
        long receivedTime;
    }

    @Before
    public void setUp() throws Exception {
        client = new OkHttpClient();
        userToken = Sample.loginBeehiveUser();
        LogUtil.debug("Kii User token:" + userToken);
        Config.enableLOGD = false;
    }

    @Test
    public void testParallelSendCommand() throws IOException {
        Device testDevice = Device.createNewVirtualDevice(userToken, "C");
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
                        sendCommand(testDevice.globalThingID, index);
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
            JSONObject actionParameters = actionItem.getJSONObject("setBri");
            int Bri = actionParameters.getInt("Bri");
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
        Device testDevice = Device.createNewVirtualDevice(userToken, "C");
        testDevice.start();
        testDevice.setUploadStateOnChanged(false);

        for (int i = 0; i < 100; i++) {
            long startTime = System.currentTimeMillis();
            sendCommand(testDevice.globalThingID, i);
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

    public void sendCommand(int globalThingID, int Bri) throws IOException {
        JSONObject jsonData = new JSONObject();
        JSONArray thingList = new JSONArray();
        thingList.put(globalThingID);
        jsonData.put("thingList", thingList);
        JSONObject command = new JSONObject();
        command.put("schema", "Lighting");
        command.put("schemaVersion", 1);
        JSONArray actions = new JSONArray();
        JSONObject setBri = new JSONObject();
        setBri.put("Bri", Bri);
        JSONObject item = new JSONObject();
        item.put("setBri", setBri);
        actions.put(item);
        command.put("actions", actions);
        jsonData.put("command", command);
        RequestBody body = RequestBody.create(Constants.JSON, jsonData.toString());
        Request request = new Request.Builder()
                .url(Constants.BeehiveBaseUrl + "/thing-if/command/single")
                .header("Content-Type", Constants.JSON.toString())
                .header("Authorization", "Bearer " + userToken)
                .post(body)
                .build();
        client.newCall(request).execute();
    }

}