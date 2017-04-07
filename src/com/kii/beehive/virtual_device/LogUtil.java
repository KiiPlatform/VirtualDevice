package com.kii.beehive.virtual_device;

/**
 * Created by Evan on 2016/11/4.
 */
public class LogUtil {
    public static void debug(String msg) {
        if (Config.enableLOGD) {
            System.out.println(msg);
        }
    }

    public static void error(String msg) {
        System.err.println(msg);
    }
}
