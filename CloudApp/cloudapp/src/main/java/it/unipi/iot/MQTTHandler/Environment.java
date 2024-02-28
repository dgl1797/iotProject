package it.unipi.iot.MQTTHandler;

import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import it.unipi.iot.DAOs.EnvironmentDAO;
import it.unipi.iot.Models.EnvironmentData;
import it.unipi.iot.Utils.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class Environment implements MqttCallback, IMqttMessageListener, Runnable {
  String brokerUrl;
  String clientId;
  String topic;
  private MqttClient mqttClient;
  BlockingDeque<String> queue;

  int actualState = 0; // 0 = OFF; 1 = HEATING; 2 = COOLING
  final static int[] tresholds = { 10, 30 }; // below or higher is activated until 20 is reached again

  public Environment(String brokerUrl)
      throws MqttException, ConnectorException, IOException, SQLException {
    this.brokerUrl = brokerUrl;
    this.clientId = "environment";
    this.topic = "tenv";
    mqttClient = new MqttClient(brokerUrl, clientId);
    this.queue = new LinkedBlockingDeque<>(5000);
    mqttClient.setCallback(this);
    mqttClient.connect();
    mqttClient.subscribe(topic);
    Logger.SUCCESS("env", "Connected to MQTT Broker. Subscribed to topic: " + topic);
  }

  @Override
  public void connectionLost(Throwable throwable) {
    Logger.ERROR("env", "Connection to MQTT Broker lost!");
    int reconnectTime = 3000;
    while (!mqttClient.isConnected()) {
      try {
        Thread.sleep(reconnectTime);
      } catch (InterruptedException e) {
        Logger.ERROR("env", "Error during reading waiting connection\n");
        e.printStackTrace(System.err);
        e.getMessage();
      }
      Logger.INFO("env", "MQTT Reconnecting");
      try {
        mqttClient.connect();
        Logger.SUCCESS("env", "MQTT Reconnetted");

      } catch (MqttException e) {
        Logger.ERROR("env", "Error in connection to MQTT Broker\n");
        e.printStackTrace(System.err);
        e.getMessage();
      }
      try {
        mqttClient.subscribe(topic);
        Logger.SUCCESS("env", "Connected to MQTT Broker. Subscribed to topic: " + topic);
      } catch (MqttException e) {
        Logger.ERROR("env", "Error in subsciption to " + topic + "\n");
        e.printStackTrace(System.err);
        e.getMessage();
      }

    }
  }

  private String getMode(int mode) {
    return mode == 0 ? "OFF" : (mode == 1 ? "Heating" : "Cooling");
  }

  public boolean notifyActuation(int newState) {
    actualState = newState;
    String content = String.format("{\"ta\":\"%d\"}", newState);
    MqttMessage message = new MqttMessage(content.getBytes());
    message.setQos(0);
    try {
      mqttClient.publish("actuators/env_state", message);
      Logger.SUCCESS("env",
          String.format("Conditioner mode: %s, published correctly", getMode(newState)));
      return true;
    } catch (MqttException e) {
      Logger.ERROR("env", "Error during publishing phase");
      e.printStackTrace();
      return false;
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
        Logger.ERROR("env", "Error during taking message from MQTT queue");
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
        Logger.INFO("env", String.format("New data received: %d°C", temperature));
        environmentTemperatureController(temperature);
        EnvironmentData storedData = EnvironmentDAO.saveData(new EnvironmentData(temperature));
        if (storedData == null)
          throw new SQLException("Failed to store data");
        Logger.SUCCESS("env", String.format("stored data: %s", storedData));
      } catch (ParseException e) {
        Logger.ERROR("env", "Error during parsing JSON Message");
        e.printStackTrace(System.err);
      } catch (SQLException e) {
        Logger.ERROR("env", "Failed to store data");
      }
    }
  }

  // controller functions
  void environmentTemperatureController(int temperature) {
    switch (actualState) {
      case 0:
        // No Actuation
        if (temperature < tresholds[0]) {
          Logger.INFO("env", temperature + "°C => Switching to Heating mode");
          notifyActuation(1);
          // post Heating command to CoAP node
        } else if (temperature > tresholds[1]) {
          Logger.INFO("env", temperature + "°C => Switching to Cooling mode");
          notifyActuation(2);
          // post Cooling command to CoAP node
        }
        break;
      case 1:
        // Heating mode active
        if (temperature > tresholds[1]) {
          Logger.INFO("env", temperature + "°C => Switching to Cooling mode");
          notifyActuation(2);
          // post Cooling command to CoAP node
        } else if (temperature >= tresholds[0] + 10) {
          Logger.INFO("env", temperature + "°C => Switching Conditioner Off");
          notifyActuation(0);
          // post Off command to CoAP node
        }
        break;
      case 2:
        // Cooling mode active
        if (temperature < tresholds[0]) {
          Logger.INFO("env", temperature + "°C => Switching to Heating mode");
          notifyActuation(1);
          // post Heating command to CoAP node
        } else if (temperature <= tresholds[1] - 10) {
          Logger.INFO("env", temperature + "°C => Switching Conditioner Off");
          notifyActuation(0);
          // post Off command to CoAP node
        }
        break;
      default:
        break;
    }
  }
}