package com.kii.virtualdevice;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Evan on 2017/1/17.
 */
public class DeviceTest {
    @Test
    public void createNewDevice() throws Exception {
        JSONObject user = KiiUtils.loginKiiCloud("evan", "123456");
        Device device = Device.createNewDevice(user.optString("id"), user.optString("access_token"), "Lamp", "V1");
        Assert.assertNotNull(device);
    }

    @Test
    public void createNewDeviceWithVID() throws Exception {
        JSONObject user = KiiUtils.loginKiiCloud("evan", "123456");
        Device device = Device.createNewDevice("VirtualDevice_6e153cbc-eeac-4862-8dae-ebf9baaed08a", user.optString("id"), user.optString("access_token"), "Lamp", "V1");
        Assert.assertNotNull(device);
    }

    @Test
    public void listDevices() throws Exception {
        JSONObject user = KiiUtils.loginKiiCloud("evan", "123456");
        List<Device> list = Device.listDevices(user.optString("access_token"));
        Assert.assertNotEquals(list.size(), 0);
    }

    public static void main(String[] args) {
        JSONObject user = KiiUtils.loginKiiCloud("evan", "123456");
        Device device = Device.createNewDevice("VirtualDevice_6e153cbc-eeac-4862-8dae-ebf9baaed08a", user.optString("id"), user.optString("access_token"), "Lamp", "V1");
        try {
            device.start();
//            device.setGenRandomStatePeriod(10);
            List<String> name = new ArrayList<>();
            name.add("brightness");
            List<String> value = new ArrayList<>();
            value.add("47");
            device.setStates("LampAlias",name,value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}