package com.kii.virtualdevice;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by Evan on 2017/1/17.
 */
public class Config {

    public static boolean enableLOGD = true;

    public static String KiiAppId = null;
    public static String KiiSiteUrl = null;

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
            KiiSiteUrl = json.getString("kiiSite");
            switch (KiiSiteUrl) {
                case "JP":
                    KiiSiteUrl = "https://api-jp.kii.com/api";
                    break;
                case "US":
                    KiiSiteUrl = "https://api.kii.com/api";
                    break;
                case "CN3":
                    KiiSiteUrl = "https://api-cn3.kii.com/api";
                    break;
                case "SG":
                    KiiSiteUrl = "https://api-sg.kii.com/api";
                    break;
                case "EU":
                    KiiSiteUrl = "https://api-eu.kii.com/api";
                    break;
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            throw new RuntimeException("Cannot load Config json file");
        }
    }

}
