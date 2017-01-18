package com.kii.virtualdevice;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.json.JSONException;
import org.json.JSONObject;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by yue on 17/01/2017.
 */
public class JSHandler {

    static JSONObject interpret(String JSScript, String functionName, JSONObject input) throws ScriptException, NoSuchMethodException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        engine.getContext().setWriter(pw);
        engine.eval(JSScript);
        Invocable inv = (Invocable) engine;
        inv.invokeFunction(functionName, input.toString());

        String out = sw.toString();
        LogUtil.debug(out);

        JSONObject result = null;
        try {
            result = new JSONObject(out);
        } catch (JSONException e) {
            LogUtil.error(e.getMessage());
        }
        return result;
    }

}
