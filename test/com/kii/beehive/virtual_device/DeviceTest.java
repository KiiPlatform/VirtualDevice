package com.kii.beehive.virtual_device;

import static org.junit.Assert.assertNotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by Evan on 2016/11/10.
 */
public class DeviceTest {


	String userToken = null;
	Device testDevice = null;

	@Before
	public void setUp() throws Exception {
		Device.loadConfig();
		userToken = Sample.loginBeehiveUser();
		LogUtil.debug("Kii User token:" + userToken);
	}

	@Test
	public void createNewVirtualDevice() throws Exception {
		testDevice = Device.createNewVirtualDevice(userToken, "Lighting");
		assertNotNull("Create failed", testDevice);
		testDevice.start();
		testDevice.stop();
		testDevice.delete();
	}

	@Test
	public void deleteDevices() throws Exception {
		List<Device> devices = Device.listDevices(userToken, true);
		for (Device d : devices) {
			LogUtil.debug(d.vendorThingID);
			d.delete();
		}
	}

	@Test
	public void testLoadSchemas() throws Exception {
		Device.loadSchemas();
	}

	@Test
	public void testThingIDCheck() throws Exception {
		ArrayList<Device> devices = new ArrayList<>();
		int testSize = 200;
		String url = "http://114.215.196.178:8080/beehive-portal/api/things/queryDetailByIDs";
		JSONArray ids = new JSONArray();
		for (int i = 0; i < testSize; i++) {
			new Thread() {
				@Override
				public void run() {
					Device device = null;
					try {
						device = Device.createNewVirtualDevice(userToken, "Lighting");
						boolean done = false;
						try {
							done = device.onBoarding();
						} catch (Exception e) {

						}
						if (!done) {
							try {
								Thread.sleep(3000);
								done = device.onBoarding();
							} catch (Exception e) {
							}
						}

						if (!done) {
							try {
								Thread.sleep(3000);
								done = device.onBoarding();
							} catch (Exception e) {
							}
						}

						if (done) {
							synchronized (ids) {
								ids.put(device.globalThingID);
								devices.add(device);
							}
						}
					} catch (IOException e) {
//						e.printStackTrace();
					}
				}
			}.start();
		}
		Thread.sleep(60 * 1000);
		LogUtil.debug("Total:" + ids.length());
		RequestBody body = RequestBody.create(Constants.JSON, ids.toString());
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
				.url(url)
				.header("Authorization", "Bearer " + userToken)
				.post(body)
				.build();
		try (Response response = client.newCall(request).execute()) {
			if (response.isSuccessful()) {
				String result = response.body().string();
//				LogUtil.debug(result);
				JSONArray json = new JSONArray(result);
				for (int i = 0; i < json.length(); i++) {
					JSONObject item = json.getJSONObject(i);
					String fullKiiThingID = item.optString("fullKiiThingID");
					String vendorThingID = item.optString("vendorThingID");
					if (fullKiiThingID == null || fullKiiThingID.length() < 5) {
						LogUtil.error("Wrong fullKiiThingID" + item.toString());
						for (Device device : devices) {
							if (device.vendorThingID.equals(vendorThingID)) {
								LogUtil.debug("vid:" + vendorThingID + " thingID:" + device.thingID);
								break;
							}
						}
					} else {
						LogUtil.debug("Succ vid:" + vendorThingID + " thingID:" + fullKiiThingID);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
//		for (Device device : devices) {
//			device.delete();
//		}
	}

}