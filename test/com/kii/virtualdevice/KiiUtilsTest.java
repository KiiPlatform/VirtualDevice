package com.kii.virtualdevice;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Evan on 2017/1/17.
 */
public class KiiUtilsTest {

    @Test
    public void loginKiiCloud() throws Exception {
        JSONObject result = KiiUtils.loginKiiCloud("evan", "123456");
        System.out.println(result.toString());
    }

}