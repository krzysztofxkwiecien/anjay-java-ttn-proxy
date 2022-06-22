package com.avsystem.anjay.demo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MqttThread extends Thread {

    private final String topic = "#";

    public void run() {

        String content = "";

        try {
            content = Files.readString(Path.of("mqtt_credentials.json"));
        }
        catch(IOException e){
            System.err.println("Error opening MQTT credentials file");
            System.exit(-1);
        }

        String username = "";
        String password = "";

        try {
            JsonObject json = new Gson().fromJson(content, JsonObject.class);
            username = json.get("username").toString();
            password = json.get("password").toString();
        }catch(Exception e){
            System.err.println("Error reading MQTT credentials file");
            System.exit(-1);
        }

        try {
            Gson gson = new Gson();
            MqttClient client = new MqttClient("tcp://eu1.cloud.thethings.network:1883", "JavaClient");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("end-device-test-1@ttn");
            options.setPassword("NNSXS.4YLFQ2Q3QMZHKFTZMU5AUEUEVWJPZPAB3SIDEGQ.XSZMTQDKXAMDV2FTXCPSLNAVFLLH442BTDYLEKPXF4SKPCMQ2ZUA".toCharArray());
            client.connect(options);
            System.out.println("MQTT Client Start");
            MqttMessageSender.client = client;
            MqttMessageListener messageListener = new MqttMessageListener();
            client.subscribe(topic, messageListener);
            System.out.println("MQTT Client Listening");
        } catch (MqttException e) {
            e.printStackTrace();
        }

    }
}
