package com.kii.virtualdevice;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

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

}