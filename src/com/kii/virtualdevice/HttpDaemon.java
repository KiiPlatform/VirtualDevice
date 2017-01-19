package com.kii.virtualdevice;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 2016/11/14.
 */
public class HttpDaemon extends NanoHTTPD {

    public static final String MIME_JSON = "application/json; charset=utf-8";
    HashMap<String, HashMap<String, Device>> userMap = new HashMap<>();
    HashMap<String, String> userTokenMap = new HashMap<>();

    public static final int CODE_OK = 1;
    public static final int CODE_NO_PERMISSION = 2;
    public static final int CODE_MISSED_PARAMETERS = 3;
    public static final int CODE_VID_EXIST = 4;
    public static final int CODE_ALREADY_START = 5;
    public static final int CODE_FAILED = 6;
    public static final int CODE_NOT_START = 7;
    public static final int CODE_IO_EXCEPTION = 8;
    public static final int CODE_NOT_IMPL = 9;

    public HttpDaemon() throws IOException {
        super(Config.HTTP_PORT);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nRunning! Point your browsers to http://localhost:" + Config.HTTP_PORT + "/ \n");
    }

    public static void main(String[] args) {
        try {
            new HttpDaemon();
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
        }
    }


    protected String getParamValue(Map<String, List<String>> parameters, String key) {
        List<String> list = parameters.get(key);
        if (list == null || list.size() == 0) {
            return null;
        }
        String param = list.get(0);
        return param;
    }

    protected Response getErrResp(Response.IStatus status, int code, String msg) {
        JSONObject json = new JSONObject();
        try {
            json.put("code", code);
            json.put("msg", msg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return newFixedLengthResponse(status, MIME_JSON, json.toString());
    }

    protected Response getEmptyOKResp() {
        JSONObject json = new JSONObject();
        json.put("code", CODE_OK);
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
    }

    protected String getUserId(String accessToken) {
        String userID = userTokenMap.get(accessToken);
        if (userID != null) {
            return userID;
        }
        Request request = new Request.Builder()
                .url(Config.KiiSiteUrl + "/api/apps/" + Config.KiiAppId + "/users/me")
                .header("Content-Type", "application/vnd.kii.UserDataRetrievalResponse+json")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        OkHttpClient client = new OkHttpClient();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String result = response.body().string();
                LogUtil.debug(result);
                JSONObject json = new JSONObject(result);
                userID = json.getString("userID");
                userTokenMap.put(accessToken, userID);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return userID;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        Map<String, List<String>> parameters = session.getParameters();
        String accessToken = getParamValue(parameters, "accessToken");
        if (accessToken == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed accessToken");
        }
        String userID = getUserId(accessToken);
        if (userID == null) {
            return getErrResp(Response.Status.UNAUTHORIZED, CODE_NO_PERMISSION, "accessToken is invalid");
        }
        switch (uri) {
            case "createNewVirtualDevice":
                return createNewVirtualDevice(userID, parameters);
            case "listDevices":
                return listDevices(userID, parameters);
            case "startDevice":
                return startDevice(userID, parameters);
            case "stopDevice":
                return stopDevice(userID, parameters);
            case "deleteDevice":
                return deleteDevice(userID, parameters);
            case "getMessageQueue":
                return getMessageQueue(userID, parameters);
            case "getStates":
                return getStates(userID, parameters);
            case "setUploadStatePeriod":
                return setUploadStatePeriod(userID, parameters);
            case "setUploadStateOnChanged":
                return setUploadStateOnChanged(userID, parameters);
            case "setMessageQueueSize":
                return setMessageQueueSize(userID, parameters);
            case "setGenRandomStatePeriod":
                return setGenRandomStatePeriod(userID, parameters);
            case "setState":
                return setState(userID, parameters);
        }
        return getErrResp(Response.Status.NOT_FOUND, CODE_FAILED, "The action is not supported");
    }


    /**
     * http://localhost:8129/createNewVirtualDevice?accessToken=P7CQAmqTF2cSMJiC6pMU83BqdsfINcSCgWNyQqzPtY8&thingType=Lamp&firmwareVersion=V1
     *
     * @param parameters
     * @return
     */
    protected Response createNewVirtualDevice(String userID, Map<String, List<String>> parameters) {
        String accessToken = getParamValue(parameters, "accessToken");
        String thingType = getParamValue(parameters, "thingType");
        if (thingType == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed thingType");
        }
        String firmwareVersion = getParamValue(parameters, "firmwareVersion");
        if (firmwareVersion == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed firmwareVersion");
        }
        Device device = Device.createNewDevice(userID, accessToken, thingType, firmwareVersion);
        if (device == null) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_FAILED, "Create failed");
        }
        JSONObject json = new JSONObject();
        json.put("code", CODE_OK);
        json.put("vendorThingID", device.getVendorThingID());
        json.put("thingID", device.getThingID());
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
    }


    /**
     * http://localhost:8129/listDevices?accessToken=P7CQAmqTF2cSMJiC6pMU83BqdsfINcSCgWNyQqzPtY8
     *
     * @param userID
     * @param parameters
     * @return
     */

    protected Response listDevices(String userID, Map<String, List<String>> parameters) {
        String accessToken = getParamValue(parameters, "accessToken");
        List<Device> devices = Device.listDevices(accessToken);
        HashMap<String, Device> deviceMap = userMap.get(userID);
        JSONArray arr = new JSONArray();
        for (Device device : devices) {
            JSONObject item = new JSONObject();
            item.put("vendorThingID", device.getVendorThingID());
            item.put("thingID", device.getThingID());
            if (deviceMap != null && deviceMap.containsKey(device.getVendorThingID())) {
                device = deviceMap.get(device.getVendorThingID());
                item.put("isRunning", true);
                item.put("messageQueueSize", device.getMessageQueueSize());
                item.put("startTime", device.getStartTime());
                item.put("uploadStatePeriod", device.uploadStatePeriod);
                item.put("uploadStateOnChanged", device.uploadStateOnChanged);
                item.put("genRandomStatePeriod", device.genRandomStatePeriod);
            } else {
                item.put("isRunning", false);
                item.put("messageQueueSize", Config.DEFAULT_MSG_QUEUE_SIZE);
            }
            item.put("thingType", device.getThingType());
            item.put("firmwareVersion", device.getFirmwareVersion());
            arr.put(item);
        }
        JSONObject json = new JSONObject();
        json.put("code", CODE_OK);
        json.put("devices", arr);
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
    }

    /**
     * http://localhost:8129/startDevice?accessToken=P7CQAmqTF2cSMJiC6pMU83BqdsfINcSCgWNyQqzPtY8&vendorThingID=VirtualDevice_660ff80e-5d30-4de3-8369-f1154a6280f5
     *
     * @param userID
     * @param parameters
     * @return
     */

    protected Response startDevice(String userID, Map<String, List<String>> parameters) {
        String accessToken = getParamValue(parameters, "accessToken");
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap != null && deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.OK, CODE_ALREADY_START, "Already start");
        }
        try {
            List<Device> devices = Device.listDevices(accessToken);
            Device device = null;
            for (Device d : devices) {
                if (vendorThingID.equals(d.getVendorThingID())) {
                    device = d;
                    break;
                }
            }
            if (device == null) {
                return getErrResp(Response.Status.BAD_REQUEST, CODE_FAILED, "You do not own the device");
            }
            device.start();
            if (deviceMap == null) {
                deviceMap = new HashMap<>();
                userMap.put(userID, deviceMap);
            }
            deviceMap.put(device.getVendorThingID(), device);
            return getEmptyOKResp();
        } catch (IOException e) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_IO_EXCEPTION, e.getMessage());
        }
    }

    /**
     * localhost:8129/stopDevice?accessToken=P7CQAmqTF2cSMJiC6pMU83BqdsfINcSCgWNyQqzPtY8&vendorThingID=VirtualDevice_660ff80e-5d30-4de3-8369-f1154a6280f5
     *
     * @param userID
     * @param parameters
     * @return
     */

    protected Response stopDevice(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        device.stop();
        deviceMap.remove(vendorThingID);
        return getEmptyOKResp();
    }

    /**
     * http://localhost:8129/deleteDevice?accessToken=P7CQAmqTF2cSMJiC6pMU83BqdsfINcSCgWNyQqzPtY8&vendorThingID=VirtualDevice_b872afec-3ee0-412a-9586-e784b6d107ac
     *
     * @param userID
     * @param parameters
     * @return
     */

    protected Response deleteDevice(String userID, Map<String, List<String>> parameters) {
        String accessToken = getParamValue(parameters, "accessToken");
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }

        try {
            HashMap<String, Device> deviceMap = userMap.get(userID);
            if (deviceMap != null && deviceMap.containsKey(vendorThingID)) {
                Device device = deviceMap.remove(vendorThingID);
                device.delete();
            } else {
                List<Device> devices = Device.listDevices(accessToken);
                Device device = null;
                for (Device d : devices) {
                    if (vendorThingID.equals(d.getVendorThingID())) {
                        device = d;
                        break;
                    }
                }
                if (device == null) {
                    return getErrResp(Response.Status.BAD_REQUEST, CODE_FAILED, "You do not own the device");
                }
                device.delete();
            }
            return getEmptyOKResp();
        } catch (IOException e) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_IO_EXCEPTION, e.getMessage());
        }
    }


    /**
     * http://localhost:8129/getMessageQueue?accessToken=P7CQAmqTF2cSMJiC6pMU83BqdsfINcSCgWNyQqzPtY8&vendorThingID=VirtualDevice_6e153cbc-eeac-4862-8dae-ebf9baaed08a
     *
     * @param userID
     * @param parameters
     * @return
     */
    protected Response getMessageQueue(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        JSONArray messages = new JSONArray();
        synchronized (device) {
            for (JSONObject item : device.messageQueue) {
                messages.put(item);
            }
        }
        JSONObject json = new JSONObject();
        json.put("code", CODE_OK);
        json.put("messages", messages);
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
    }


    /**
     * localhost:8129/getStates?accessToken=P7CQAmqTF2cSMJiC6pMU83BqdsfINcSCgWNyQqzPtY8&vendorThingID=VirtualDevice_6e153cbc-eeac-4862-8dae-ebf9baaed08a
     *
     * @param userID
     * @param parameters
     * @return
     */
    protected Response getStates(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        JSONObject json = device.getStates();
        json.put("code", CODE_OK);
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString());
    }

    protected Response setUploadStatePeriod(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        String periodStr = getParamValue(parameters, "period");
        int period = -1;
        try {
            period = Integer.valueOf(periodStr);
        } catch (Exception e) {
            period = -1;
        }
        if (period < 0) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed period, or the data format is wrong");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        device.setUploadStatePeriod(period);
        return getEmptyOKResp();
    }

    protected Response setUploadStateOnChanged(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        String valueStr = getParamValue(parameters, "value");
        int value = -1;
        try {
            value = Integer.valueOf(valueStr);
        } catch (Exception e) {
            value = -1;
        }
        if (value != 0 && value != 1) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed value, or the data format is wrong (must be 0 or 1)");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        device.setUploadStateOnChanged(value == 1);
        return getEmptyOKResp();
    }

    protected Response setMessageQueueSize(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        String valueStr = getParamValue(parameters, "value");
        int value = -1;
        try {
            value = Integer.valueOf(valueStr);
        } catch (Exception e) {
            value = -1;
        }
        if (value <= 0) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed value, or the data format is wrong");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        device.setMessageQueueSize(value);
        return getEmptyOKResp();
    }

    protected Response setGenRandomStatePeriod(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        String periodStr = getParamValue(parameters, "period");
        int period = -1;
        try {
            period = Integer.valueOf(periodStr);
        } catch (Exception e) {
            period = -1;
        }
        if (period < 0) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed period, or the data format is wrong");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        device.setGenRandomStatePeriod(period);
        return getEmptyOKResp();
    }


    /**
     * http://localhost:8129/setState?accessToken=d7076719b49047621da3f8fd4e853acc82f61e63&vendorThingID=0999E-W70-C-115&field=Power&value=1&alias=LampAlias
     *
     * @param userID
     * @param parameters
     * @return
     */
    protected Response setState(String userID, Map<String, List<String>> parameters) {
        String vendorThingID = getParamValue(parameters, "vendorThingID");
        if (vendorThingID == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed vendorThingID");
        }
        String alias = getParamValue(parameters, "alias");
        if (alias == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed alias");
        }
        List<String> field = parameters.get("field");
        if (field == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed field");
        }
        List<String> value = parameters.get("value");
        if (value == null) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Missed value");
        }
        if (field.size() != value.size()) {
            return getErrResp(Response.Status.BAD_REQUEST, CODE_MISSED_PARAMETERS, "Field counts and value counts must be the same");
        }
        HashMap<String, Device> deviceMap = userMap.get(userID);
        if (deviceMap == null || !deviceMap.containsKey(vendorThingID)) {
            return getErrResp(Response.Status.INTERNAL_ERROR, CODE_NOT_START, "The device is not started");
        }
        Device device = deviceMap.get(vendorThingID);
        if (device.setStates(alias, field, value)) {
            return getEmptyOKResp();
        } else {
            return getErrResp(Response.Status.NOT_IMPLEMENTED, CODE_NOT_IMPL, "Not all the state is supported, or some value is not accepted");
        }
    }

}
