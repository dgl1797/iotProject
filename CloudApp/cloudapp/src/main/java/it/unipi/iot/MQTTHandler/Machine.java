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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class Machine implements MqttCallback, IMqttMessageListener, Runnable {
  String brokerUrl;
  String clientId;
  String topic;
  private MqttClient mqttClient;
  BlockingDeque<String> queue;
  final static int[] tempTresh = { 49, 59, 89 }; /*
                                                  * composed by a lowerBound(soft cooling), a middelValue (hard cooling)
                                                  * and a upperBound representing the negative extreme value (stopping
                                                  * machine)
                                                  */
  final static int[] productionLevel = { 10, 20 }; /*
                                                    * lowerBound, the machine is not productive and must be
                                                    * stopped, upperBound, the machine produces extremely more than
                                                    * expected and probabily wil produce bad paper
                                                    */
  int[] ac_Ac_State = { 0, 0 };
  LinkedList<Integer> productionHistory = new LinkedList<>();

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

  public boolean notifyActuation(int switchVal, int tempVal) {
    /*
     * actuator: switchVal represents the state for the switching actuator, tempVal
     * the state for the cooling actuator
     */
    String content = String.format("{\"sv\":\"%d\",\"tv\":\"%d\"}", switchVal, tempVal);
    MqttMessage message = new MqttMessage(content.getBytes());
    try {
      Logger.INFO("mah", "Trying to publish actuation message");
      mqttClient.publish(content, message);
      Logger.SUCCESS("mah",
          String.format("Switch value: %d, Temp value: %d, published correctly", switchVal, tempVal));
      return true;
    } catch (MqttException e) {
      Logger.ERROR("mah", "Error during publishing phase");
      e.printStackTrace();
      return false;
    }
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
        if (productionHistory.size() > 3)
          productionHistory.removeFirst();
        productionHistory.addLast(temperature);
        int mean = (int) Math.ceil(productionHistory.stream().reduce(0, Integer::sum) / 4);
        // production level checks
        machineProductionLevelController(mean);
        // temperature checks
        machineTemperatureController(temperature);
      } catch (ParseException e) {
        Logger.ERROR("mah", "Error during parsing JSON Message");
        e.printStackTrace(System.err);
      } catch (SQLException e) {
        Logger.ERROR("mah", "Failed to store data");
      }
    }
  }

  // controller functions
  void machineTemperatureController(int temperature) {
    boolean ret;
    switch (ac_Ac_State[0]) {
      case 0:
        // no cooling
        ret = temperature > tempTresh[2] ? notifyActuation(1, 0)
            : (temperature > tempTresh[1] ? notifyActuation(0, 2)
                : (temperature > tempTresh[0] ? notifyActuation(0, 1) : true));
        break;
      case 1:
        // soft cooling alredy enabled
        ret = temperature > tempTresh[2] ? notifyActuation(1, 0)
            : (temperature > tempTresh[1] ? notifyActuation(0, 2)
                : (temperature > tempTresh[0] - 10 ? true : notifyActuation(0, 0)));
        break;
      case 2:
        // hard cooling alredy enabled
        ret = temperature > tempTresh[2] ? notifyActuation(1, 0)
            : (temperature > tempTresh[1] - 10 ? true
                : (temperature > tempTresh[0] - 10 ? notifyActuation(0, 1) : notifyActuation(0, 0)));
        break;
      default:
        break;
    }
  }

  void machineProductionLevelController(int mean) {
    if (mean < productionLevel[0] || mean > productionLevel[1]) {
      Logger.ERROR("mah", "Problem occurred: production level too low");
      notifyActuation(1, 0);
    }
  }
}
