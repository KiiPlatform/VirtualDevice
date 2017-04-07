package com.kii.beehive.virtual_device;

import java.io.IOException;

/**
 * Created by Evan on 2016/11/9.
 */
public class Sample {


	public static String loginBeehiveUser() {
		return DeviceUtil.loginBeehiveUser("evan", "123456");
	}

	public static void main(String[] args) {
		String accessToken = loginBeehiveUser();
		LogUtil.debug("Kii User token:" + accessToken);
		Device light = null;
		try {
			light = Device.createNewVirtualDevice(accessToken, "Lighting");
//            light = Device.getDeviceInstanceFromVID(accessToken, "0999E-W70-C-115");
			light.start();
			light.setUploadStatePeriod(10);
			light.setGenRandomStatePeriod(10);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
