package com.kii.beehive.virtual_device;

import okhttp3.MediaType;

/**
 * Created by Evan on 2016/11/4.
 */
public class Constants {

    public static final String VIRTUAL_DEVICE_VID_PREFIX = "0999E";

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static int QoS = 0;

    public static int HTTP_PORT = 8129;

    public static int DEFAULT_MSG_QUEUE_SIZE = 100;

    public static String KiiAppId = null;
    public static String BeehiveBaseUrl = null;
}
