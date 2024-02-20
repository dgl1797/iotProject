package it.unipi.iot.MQTTHandler;

import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import it.unipi.iot.DAOs.MachineDAO;
import it.unipi.iot.Models.MachineData;
import it.unipi.iot.Utils.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class Machine implements MqttCallback, IMqttMessageListener, Runnable {
  String brokerUrl;
  String clientId;
  String topic;
  private MqttClient mqttClient;
  BlockingDeque<String> queue;

  public Machine(String brokerUrl)
      throws MqttException, ConnectorException, IOException, SQLException {
    this.brokerUrl = brokerUrl;
    this.clientId = "machine";
    this.topic = "mdata";
    mqttClient = new MqttClient(brokerUrl, clientId);
    this.queue = new LinkedBlockingDeque<>(5000);
    mqttClient.setCallback(this);
    mqttClient.connect();
    mqttClient.subscribe(topic);
    Logger.SUCCESS("mah", "Connected to MQTT Broker. Subscribed to topic: " + topic);
  }

  @Override
  public void connectionLost(Throwable throwable) {
    Logger.ERROR("mah", "Connection to MQTT Broker lost!");
    int reconnectTime = 3000;
    while (!mqttClient.isConnected()) {
      try {
        Thread.sleep(reconnectTime);
      } catch (InterruptedException e) {
        Logger.ERROR("mah", "Error during reading waiting connection\n");
        e.printStackTrace(System.err);
        e.getMessage();
      }
      Logger.INFO("mah", "MQTT Reconnecting");
      try {
        mqttClient.connect();
        Logger.SUCCESS("mah", "MQTT Reconnetted");

      } catch (MqttException e) {
        Logger.ERROR("mah", "Error in connection to MQTT Broker\n");
        e.printStackTrace(System.err);
        e.getMessage();
      }

      try {
        mqttClient.subscribe(topic);
        Logger.SUCCESS("mah", "Connected to MQTT Broker. Subscribed to topic: " + topic);
      } catch (MqttException e) {
        Logger.ERROR("mah", "Error in subsciption to " + topic + "\n");
        e.printStackTrace(System.err);
        e.getMessage();
      }

    }
  }

  @Override
  public void messageArrived(String topic, MqttMessage mqttMessage) throws InterruptedException {
    String msg = new String(mqttMessage.getPayload());
    queue.put(msg);
  }

  @Override
  public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
  }

  @Override
  public void run() {

    int queueCapacity = queue.remainingCapacity();
    int treshold = (int) (queueCapacity * 0.75);

    while (true) {
      String msg = null;
      try {
        msg = queue.take();
      } catch (InterruptedException e) {
        System.err.println("[MAH FAIL] - Error during taking message from MQTT queue");
      }

      if (queue.size() > treshold) {
        for (int i = 0; i < 1750; i++) {
          queue.remove();
        }
      }
      JSONObject json;

      try {
        json = (JSONObject) JSONValue.parseWithException(msg);
        int temperature = ((Number) json.get("temperature")).intValue();
        int humidity = ((Number) json.get("humidity")).intValue();
        int outputs = ((Number) json.get("outputs")).intValue();
        Logger.INFO("mah", String.format("New data received: %d; %d; %d", temperature, humidity, outputs));
        MachineData returnedData = MachineDAO.saveData(new MachineData(temperature, humidity, outputs));
        if (returnedData == null)
          throw new SQLException("Failed to store data");
        Logger.SUCCESS("mah", String.format("stored data: %s", returnedData));
      } catch (ParseException e) {
        Logger.ERROR("mah", "Error during parsing JSON Message");
        e.printStackTrace(System.err);
      } catch (SQLException e) {
        Logger.ERROR("mah", "Failed to store data");
      }
    }
  }
}
