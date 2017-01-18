package com.kii.virtualdevice;

import org.json.JSONObject;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Created by yue on 17/01/2017.
 */
public class JSHandler {

    static Object interpret(String JSScript, String functionName, JSONObject input) throws ScriptException, NoSuchMethodException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        engine.eval(JSScript);
        Invocable inv = (Invocable) engine;
        inv.invokeFunction(functionName, input);
        Object output = engine.get("output");
        return output;
    }

}
