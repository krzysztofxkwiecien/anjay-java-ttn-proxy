package com.avsystem.anjay.demo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.List;
import java.util.Set;

public class MqttMessageListener implements IMqttMessageListener {

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        byte[] payload = mqttMessage.getPayload();
        String payloadString = new String(payload);

        if(topic.equals("v3/end-device-test-1@ttn/devices/eui-0080e115000ad365/up")){
            JsonObject json = new Gson().fromJson(payloadString, JsonObject.class);
            String timestamp = json.get("received_at").toString();
            String deviceId = json.getAsJsonObject("end_device_ids").get("device_id").toString();
            JsonObject decodedPayload = json.getAsJsonObject("uplink_message").getAsJsonObject("decoded_payload");

            System.out.println("### New MQTT message ### " + timestamp);
            System.out.println("Device: " + deviceId);
            System.out.println(decodedPayload);
            System.out.println("### End ### ");

            mapCayenneObjects(decodedPayload);
        }
        else{
            System.out.println(topic);
            System.out.println(payloadString);
        }

    }

    private void mapCayenneObjects(JsonObject decodedPayload){
        Set<String> cayenneObjectNames = decodedPayload.keySet();
        for(String cayenneObjectName : cayenneObjectNames){
            if(cayenneObjectName.startsWith("temperature"))
                updateThermometer(decodedPayload.get(cayenneObjectName).getAsDouble());
            else if(cayenneObjectName.startsWith("accelerometer"))
                updateAccelerometer(decodedPayload.getAsJsonObject(cayenneObjectName));
            else if(cayenneObjectName.startsWith("digital_out"))
                updateLed(decodedPayload.get(cayenneObjectName).getAsInt());
        }
    }

    private void updateThermometer(double value){
        RegisteredObjects.thermometer.setValue(value);
        System.out.println("Updated temperature: " + value);
    }

    private void updateAccelerometer(JsonObject accelerometerObject){
        double x = accelerometerObject.get("x").getAsDouble();
        double y = accelerometerObject.get("y").getAsDouble();
        double z = accelerometerObject.get("z").getAsDouble();
        RegisteredObjects.accelerometer.setX(x);
        RegisteredObjects.accelerometer.setY(y);
        RegisteredObjects.accelerometer.setZ(z);
        System.out.println("Updated acceleration: " + x + " " + y + " " + z);
    }

    private void updateLed(int state){
        RegisteredObjects.led.setState(state == 1);
        System.out.println("Updated led: " + state);
    }
}
