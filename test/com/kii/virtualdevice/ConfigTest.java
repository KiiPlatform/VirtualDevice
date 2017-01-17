package com.kii.virtualdevice;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Evan on 2017/1/17.
 */
public class ConfigTest {
    @Test
    public void loadConfig() throws Exception {
        Config.loadConfig();
        Assert.assertNotNull(Config.KiiAppId);
    }

}