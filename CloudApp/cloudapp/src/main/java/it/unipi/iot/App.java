package it.unipi.iot;

import it.unipi.iot.MQTTHandler.Environment;

import java.io.IOException;
import java.sql.SQLException;

import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.MqttException;

public class App {
    public static void main(String[] args)
            throws MqttException, ConnectorException, IOException, SQLException, InterruptedException {
        String topic = "tenv";
        String brokerUrl = "tcp://127.0.0.1:1883";
        String clientId = "environment";

        // MQTTs
        Environment env = new Environment(brokerUrl, clientId, topic);

        // threads
        Thread envThread = new Thread(env);
        envThread.start();
    }
}
