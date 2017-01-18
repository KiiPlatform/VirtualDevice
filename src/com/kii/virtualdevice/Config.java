package com.kii.virtualdevice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by Evan on 2017/1/17.
 */
public class Config {

    public static boolean enableLOGD = true;
    public static final String THING_PASSWORD = "virtual_device";
    public static final String VIRTUAL_DEVICE_BUCKET = "virtual_device";
    public static int QoS = 0;
    public static int HTTP_PORT = 8129;
    public static int DEFAULT_MSG_QUEUE_SIZE = 100;


    public static String KiiAppId = null;
    public static String KiiAppKey = null;
    public static String KiiSiteUrl = null;
    public static JSONArray SupportedTypes = null;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        File file = new File("config.json");
        String fileData = null;
        try {
            fileData = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            throw new RuntimeException("Cannot read Config json file");
        }
        try {
            JSONObject json = new JSONObject(fileData);
            KiiAppId = json.getString("kiiAppId");
            KiiAppKey = json.getString("kiiAppKey");
            KiiSiteUrl = json.getString("kiiSite");
            switch (KiiSiteUrl) {
                case "JP":
                    KiiSiteUrl = "https://api-jp.kii.com";
                    break;
                case "US":
                    KiiSiteUrl = "https://api.kii.com";
                    break;
                case "CN3":
                    KiiSiteUrl = "https://api-cn3.kii.com";
                    break;
                case "SG":
                    KiiSiteUrl = "https://api-sg.kii.com";
                    break;
                case "EU":
                    KiiSiteUrl = "https://api-eu.kii.com";
                    break;
            }
            SupportedTypes = json.getJSONArray("SupportedTypes");
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            throw new RuntimeException("Cannot load Config json file");
        }
    }

}
