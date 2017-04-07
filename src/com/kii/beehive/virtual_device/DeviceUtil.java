package com.kii.beehive.virtual_device;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by Evan on 2016/11/3.
 */
public class DeviceUtil {

	public static String loginBeehiveUser(String username, String password) {
		JSONObject json = new JSONObject();
		json.put("userName", username);
		json.put("password", password);
		RequestBody body = RequestBody.create(Constants.JSON, json.toString());
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
				.url(Constants.BeehiveBaseUrl + "/oauth2/login")
				.post(body)
				.build();
		try (Response response = client.newCall(request).execute()) {
			if (response.isSuccessful()) {
				String result = response.body().string();
				JSONObject root = new JSONObject(result);
				return root.getString("accessToken");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String getDeviceTypeCode(String vendorThingID) {
		if (vendorThingID == null) {
			return null;
		}
		String[] components = vendorThingID.split("-");
		if (components.length > 2) {
			return components[2];
		}
		return null;
	}

	public static void updateSchemas(String accessToken) {
		OkHttpClient httpClient = new OkHttpClient();
		for (String s : Device.SupportedDeviceTypesMap.keySet()) {
			Request request = new Request.Builder()
					.url(Constants.BeehiveBaseUrl + "/schema/query/industrytemplate?thingType="
							+ s + "&name=" + s + "&version=1")
					.header("Authorization", "Bearer " + accessToken)
					.build();
			try (Response response = httpClient.newCall(request).execute()) {
				if (response.isSuccessful()) {
					File file = new File(s + ".json");
					try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
						out.println(response.body().string());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Download schema '" + s + "' failed");
			}
		}
		Device.loadSchemas();
	}

}
