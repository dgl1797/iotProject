package it.unipi.iot;

import it.unipi.iot.MQTTHandler.Environment;
import it.unipi.iot.MQTTHandler.Machine;

import java.io.IOException;
import java.sql.SQLException;

import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.MqttException;

public class App {
    public static void main(String[] args)
            throws MqttException, ConnectorException, IOException, SQLException, InterruptedException {
        String brokerUrl = "tcp://127.0.0.1:1883";

        // MQTTs
        Environment env = new Environment(brokerUrl);
        Machine mah = new Machine(brokerUrl);
        // threads
        Thread envThread = new Thread(env);
        envThread.start();

        Thread mahThread = new Thread(mah);
        mahThread.start();
    }
}
