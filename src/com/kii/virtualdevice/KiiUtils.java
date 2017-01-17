package com.kii.virtualdevice;

import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by Evan on 2017/1/17.
 */
public class KiiUtils {
    static JSONObject loginKiiCloud(String username, String password) {
        JSONObject json = new JSONObject();
        try {
            json.put("grant_type", "password");
            json.put("username", username);
            json.put("password", password);
        } catch (JSONException e) {
        }
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/api/apps/" + Config.KiiAppId + "/oauth2/token")
                .post(body)
                .build();
        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String result = response.body().string();
                JSONObject jsonObject = null;
                jsonObject = new JSONObject(result);
                return jsonObject;
            }
        } catch (IOException e) {
            LogUtil.error(e.getMessage());
        } catch (JSONException e) {
            LogUtil.error(e.getMessage());
        }
        return null;
    }
}
