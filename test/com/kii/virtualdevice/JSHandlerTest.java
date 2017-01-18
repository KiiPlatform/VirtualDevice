package com.kii.virtualdevice;

import com.oracle.tools.packager.Log;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by yue on 18/01/2017.
 */
public class JSHandlerTest {

    @Test
    public void runJSHandler() throws Exception {
        File file = new File("LampAlias.js");
        String fileData = null;
        try {
            fileData = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            throw new RuntimeException("Cannot read Lamp Alias js");
        }
        LogUtil.debug(fileData);

        JSONObject input = new JSONObject();
        JSONObject action = new JSONObject();
        JSONObject states = new JSONObject();
        action.put("setPower", true);
        states.put("brightness", 45);
        states.put("power", false);
        states.put("battery", 98);
        input.put("action", action);
        input.put("states", states);

        JSONObject out = JSHandler.interpret(fileData, "run", input);
        Assert.assertEquals(out.optBoolean("power"), true);

        action.remove("setPower");
        action.put("setBrightness", 47);
        out = JSHandler.interpret(fileData, "run", input);
        Assert.assertEquals(out.optInt("brightness"), 47);
    }
}
