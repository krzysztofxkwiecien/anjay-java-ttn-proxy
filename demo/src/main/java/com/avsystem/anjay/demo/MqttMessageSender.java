package com.avsystem.anjay.demo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttMessageSender {

    public static MqttClient client;
    private static final String topic = "v3/end-device-test-1@ttn/devices/eui-0080e115000ad365/down/replace";

    private static String ledCode[] = {"AA==", "AQ=="};

    public static void sendLedDownlink(boolean state) throws MqttException {

        JsonObject json = new JsonObject();
        json.addProperty("f_port", 2);
        json.addProperty("frm_payload", ledCode[state ? 1 : 0]);
        json.addProperty("priority", "NORMAL");

        JsonArray arr = new JsonArray();
        arr.add(json);

        JsonObject finalJson = new JsonObject();
        finalJson.add("downlinks", arr);

        System.out.println("PAYLOAD: " + finalJson.toString());

        byte[] payload = finalJson.toString().getBytes();

        MqttMessage msg = new MqttMessage(payload);
        msg.setQos(0);
        msg.setRetained(true);

        client.publish(topic, msg);
    }
}
