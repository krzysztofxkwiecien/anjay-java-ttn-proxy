package com.avsystem.anjay.demo;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

public class MqttThread extends Thread {

    private final String topic = "#";

    public void run() {

        try {
            MqttClient client = new MqttClient("tcp://eu1.cloud.thethings.network:1883", "JavaClient");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("");
            options.setPassword("".toCharArray());
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
