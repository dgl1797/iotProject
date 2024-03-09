package it.unipi.iot.Controllers;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import it.unipi.iot.DAOs.EnvironmentDAO;
import it.unipi.iot.Models.EnvironmentData;
import it.unipi.iot.Utils.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class Environment implements MqttCallback, IMqttMessageListener, Runnable {
  private String topic;
  private final MqttClient mqttClient;
  private final int NODE_ID = 1;
  private final String RESOURCE = "environment/temp_act";
  private BlockingDeque<String> queue;

  private boolean stateForced = false;
  private int actualState = 0; // 0 = OFF; 1 = HEATING; 2 = COOLING
  private final static int[] tresholds = { 10, 30 }; // below or higher is activated until 20 is reached again

  public Environment(String brokerUrl)
      throws MqttException, ConnectorException, IOException, SQLException {

    this.topic = "tenv";
    /** MQTT Setup */
    mqttClient = new MqttClient(brokerUrl, "environment");
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
          String.format("Conditioner mode changed: %s", getMode(newState)));
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
        if (!stateForced)
          environmentTemperatureController(temperature);
        EnvironmentData storedData = EnvironmentDAO.saveData(new EnvironmentData(temperature, actualState));
        if (storedData == null)
          throw new SQLException("Failed to store data");
      } catch (ParseException e) {
        Logger.ERROR("env", "Error during parsing JSON Message");
        e.printStackTrace(System.err);
      } catch (SQLException e) {
        Logger.ERROR("env", "Failed to store data");
      }
    }
  }

  private int getState(String mode) {
    mode = mode.toLowerCase();
    return mode.equals("off") ? 0 : mode.equals("heating") ? 1 : mode.equals("cooling") ? 2 : -1;
  }

  public void releaseState() {
    Logger.SUCCESS("env", "Environment actuation released");
    this.stateForced = false;
  }

  public boolean forceState(String newMode) {
    this.stateForced = true;
    int newState = getState(newMode);
    if (newState == -1) {
      Logger.ERROR("env", "Failed to force conditioner's state");
      return false;
    }
    if (CoAPSender.sendCommand(String.format("{\"ta\":\"%d\"}", newState), NODE_ID, RESOURCE)) {
      Logger.INFO("env", String.format("Forcing Conditioner to %s mode", newMode));
      notifyActuation(newState);
      return true;
    } else {
      Logger.ERROR("env", "Failed to force conditioner's state");
      return false;
    }
  }

  // controller functions
  void environmentTemperatureController(int temperature) {
    switch (actualState) {
      case 0:
        // No Actuation
        if (temperature < tresholds[0]) {
          if (CoAPSender.sendCommand("{\"ta\":\"1\"}", NODE_ID, RESOURCE)) {
            // post Heating command to CoAP node
            notifyActuation(1);
          }
        } else if (temperature > tresholds[1]) {
          if (CoAPSender.sendCommand("{\"ta\":\"2\"}", NODE_ID, RESOURCE)) {
            // post Cooling command to CoAP node
            notifyActuation(2);
          }
        }
        break;
      case 1:
        // Heating mode active
        if (temperature > tresholds[1]) {
          if (CoAPSender.sendCommand("{\"ta\":\"2\"}", NODE_ID, RESOURCE)) {
            // post Cooling command to CoAP node
            notifyActuation(2);
          }
        } else if (temperature >= tresholds[0] + 10) {
          if (CoAPSender.sendCommand("{\"ta\":\"0\"}", NODE_ID, RESOURCE)) {
            // post Off command to CoAP node
            notifyActuation(0);
          }
        }
        break;
      case 2:
        // Cooling mode active
        if (temperature < tresholds[0]) {
          if (CoAPSender.sendCommand("{\"ta\":\"1\"}", NODE_ID, RESOURCE)) {
            // post Heating command to CoAP node
            notifyActuation(1);
          }
        } else if (temperature <= tresholds[1] - 10) {
          if (CoAPSender.sendCommand("{\"ta\":\"0\"}", NODE_ID, RESOURCE)) {
            // post Off command to CoAP node
            notifyActuation(0);
          }
        }
        break;
      default:
        break;
    }
  }

  public void handlePOST(CoapExchange exchange) {
    try {
      if (!forceState(getMode((actualState + 1) % 3)))
        throw new Throwable("Couldn't force state");
      Response response = new Response(CoAP.ResponseCode.CONTENT);
      response.setPayload("{\"res\":\"success\"}");
      exchange.respond(response);
    } catch (Throwable e) {
      Logger.ERROR("coap", "Failed to parse message");
      e.printStackTrace();
      exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE, "Failed".getBytes(StandardCharsets.UTF_8));
    }
  }
}