package it.unipi.iot.Controllers;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import it.unipi.iot.DAOs.MachineDAO;
import it.unipi.iot.Models.MachineData;
import it.unipi.iot.Utils.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class Machine implements MqttCallback, IMqttMessageListener, Runnable {
  String brokerUrl;
  String clientId;
  String topic;
  private MqttClient mqttClient;
  private final int NODE_ID = 2;
  BlockingDeque<String> queue;
  final static int[] tempTresh = { 49, 59, 89 }; /*
                                                  * composed by a lowerBound(soft cooling), a middelValue (hard cooling)
                                                  * and a upperBound representing the negative extreme value (stopping
                                                  * machine)
                                                  */
  final static int[] productionLevel = { 10, 30 }; /*
                                                    * lowerBound, the machine is not productive and must be
                                                    * stopped, upperBound, the machine produces extremely more than
                                                    * expected and probabily wil produce bad paper
                                                    */
  int[] ac_Ac_State = { 0, 0 }; // ac_Ac_State[0] is cooler; ac_Ac_State[1] is switch
  LinkedList<Integer> productionHistory = new LinkedList<>();

  private boolean forcedSwitch = false;
  private boolean forcedCooler = false;

  private final String SWITCH_RESOURCE = "machine/switch_act";
  private final String TEMP_RESOURCE = "machine/temp_act";

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

  private String getTMode(int tval) {
    return tval == 0 ? "OFF" : (tval == 1 ? "Medium" : "High");
  }

  private String getSMode(int sval) {
    return sval == 0 ? "ON" : "OFF";
  }

  public boolean notifyActuation(int switchVal, int tempVal) {
    /*
     * actuator: switchVal represents the state for the switching actuator, tempVal
     * the state for the cooling actuator
     */
    ac_Ac_State[0] = tempVal;
    ac_Ac_State[1] = switchVal;
    String content = String.format("{\"sv\":\"%d\",\"tv\":\"%d\"}", switchVal, tempVal);
    MqttMessage message = new MqttMessage(content.getBytes());
    try {
      mqttClient.publish("actuators/mah_state", message);
      Logger.SUCCESS("mah",
          String.format("State changed: Switch -> %s; Cooler -> %s", getSMode(switchVal),
              getTMode(tempVal)));
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
        Logger.INFO("mah",
            String.format("New data received: %d°C; %d%c; %d o/min", temperature, humidity, '%', outputs));
        if (productionHistory.size() > 3)
          productionHistory.removeFirst();
        productionHistory.addLast(outputs);
        int mean = (int) Math.ceil(productionHistory.stream().reduce(0, Integer::sum) / 4);
        // production level checks
        if (productionHistory.size() > 3)
          machineProductionLevelController(mean);
        // temperature checks
        if (ac_Ac_State[1] != 1)
          machineTemperatureController(temperature);
        MachineData returnedData = MachineDAO.saveData(new MachineData(temperature, humidity, outputs, ac_Ac_State[0]));
        if (returnedData == null)
          throw new SQLException("Failed to store data");
      } catch (ParseException e) {
        Logger.ERROR("mah", "Error during parsing JSON Message");
        e.printStackTrace(System.err);
      } catch (SQLException e) {
        Logger.ERROR("mah", "Failed to store data");
      }
    }
  }

  public void releaseSwitch() {
    Logger.SUCCESS("mah", "Switch actuation released");
    forcedSwitch = false;
  }

  private int getState(String mode) {
    mode = mode.toLowerCase();
    return mode.equals("off") ? 1 : mode.equals("on") ? 0 : -1;
  }

  public boolean forceSwitchState(String newMode) {
    forcedSwitch = true;
    int newState = getState(newMode);
    if (newState == -1) {
      Logger.ERROR("mah", "Invalid mode");
      return false;
    }
    if (CoAPSender.sendCommand(String.format("{\"sa\":\"%d\"}", newState), NODE_ID, SWITCH_RESOURCE)) {
      if ((newState == 0) && (ac_Ac_State[1] == 1)) {
        resetMachine("Forced Machine Boot");
      } else if ((newState == 1) && (ac_Ac_State[1] == 0)) {
        shutdownMachine("Forced shutdown");
      }
      return true;
    }
    Logger.ERROR("mah", "Failed to force switch state");
    return false;
  }

  public void releaseCooler() {
    Logger.SUCCESS("mah", "Cooler actuation released");
    forcedCooler = false;
  }

  private int getCState(String mode) {
    mode = mode.toLowerCase();
    return mode.equals("off") ? 0 : mode.equals("medium") ? 1 : mode.equals("high") ? 2 : -1;
  }

  public boolean forceCoolerState(String newMode) {
    if (ac_Ac_State[1] == 1)
      return false;
    forcedCooler = true;
    int newState = getCState(newMode);
    if (newState == -1) {
      Logger.ERROR("mah", "Invalid mode");
      return false;
    }
    if (CoAPSender.sendCommand(String.format("{\"ta\":\"%d\"}", newState), NODE_ID, TEMP_RESOURCE)) {
      Logger.INFO("mah", String.format("Forcing Cooler to %s mode", newMode));
      notifyActuation(ac_Ac_State[1], newState);
      return true;
    }
    Logger.ERROR("mah", "Failed to force cooler state");
    return false;
  }

  // controller functions
  boolean machineTemperatureController(int temperature) {
    boolean ret = false;
    switch (ac_Ac_State[0]) {
      case 0:
        // no cooling
        if (temperature > tempTresh[2] && !forcedSwitch) {
          if (CoAPSender.sendCommand("{\"sa\":\"1\"}", NODE_ID, SWITCH_RESOURCE)) {
            // post s:off,t:off command to CoAP
            shutdownMachine("Problem occurred: Critical Temperature Reached: " + temperature + "°C");
          }
        } else if (temperature > tempTresh[1] && !forcedCooler) {
          if (CoAPSender.sendCommand("{\"ta\":\"2\"}", NODE_ID, TEMP_RESOURCE)) {
            // post s:old_s,t:high command to CoAP
            notifyActuation(0, 2);
          }
        } else if (temperature > tempTresh[0] && !forcedCooler) {
          if (CoAPSender.sendCommand("{\"ta\":\"1\"}", NODE_ID, TEMP_RESOURCE)) {
            // post s:old_s,t:medium command to CoAP
            notifyActuation(0, 1);
          }
        }
        break;
      case 1:
        // soft cooling alredy enabled
        if (temperature > tempTresh[2] && !forcedSwitch) {
          if (CoAPSender.sendCommand("{\"sa\":\"1\"}", NODE_ID, SWITCH_RESOURCE)) {
            // post s:off,t:off command to CoAP
            shutdownMachine("Problem occurred: Critical Temperature Reached: " + temperature + "°C");
          }
        } else if (temperature > tempTresh[1] && !forcedCooler) {
          if (CoAPSender.sendCommand("{\"ta\":\"2\"}", NODE_ID, TEMP_RESOURCE)) {
            // post s:old_s,t:high command to CoAP
            notifyActuation(0, 2);
          }
        } else if ((temperature <= tempTresh[0] - 10) && !forcedCooler) {
          if (CoAPSender.sendCommand("{\"ta\":\"0\"}", NODE_ID, TEMP_RESOURCE)) {
            // post s:old_s,t:off command to CoAP
            notifyActuation(0, 0);
          }
        }
        break;
      case 2:
        // hard cooling alredy enabled
        if (temperature > tempTresh[2] && !forcedSwitch) {
          if (CoAPSender.sendCommand("{\"sa\":\"1\"}", NODE_ID, SWITCH_RESOURCE)) {
            // post s:off,t:off command to CoAP
            shutdownMachine("Problem occurred: Critical Temperature Reached: " + temperature + "°C");
          }
        } else if ((temperature <= tempTresh[0] - 10) && !forcedCooler) {
          if (CoAPSender.sendCommand("{\"ta\":\"0\"}", NODE_ID, TEMP_RESOURCE)) {
            // post s:old_s,t:off command to CoAP
            notifyActuation(0, 0);
          }
        } else if ((temperature <= tempTresh[1] - 10) && !forcedCooler) {
          if (CoAPSender.sendCommand("{\"ta\":\"1\"}", NODE_ID, TEMP_RESOURCE)) {
            // post s:old_s,t:medium command to CoAP
            notifyActuation(0, 1);
          }
        }
        break;
      default:
        break;
    }
    return ret;
  }

  void machineProductionLevelController(int mean) {
    if ((mean < productionLevel[0] || mean > productionLevel[1]) && !forcedSwitch) {
      // post Off command to CoAP Switch
      if (CoAPSender.sendCommand("{\"sa\":\"1\"}", NODE_ID, SWITCH_RESOURCE))
        shutdownMachine("Problem occurred: production level anomaly detected: " + mean + " outputs/min");
    }
  }

  void resetMachine(String message) {
    productionHistory.clear();
    Logger.SUCCESS("mah", message);
    notifyActuation(0, 0);
  }

  void shutdownMachine(String message) {
    Logger.ERROR("mah", message);
    notifyActuation(1, 0);
  }

  public void handlePOST(CoapExchange exchange) {
    final String msg = exchange.getRequestText();
    try {
      final JSONObject json = (JSONObject) JSONValue.parseWithException(msg);
      String mode = json.get("type").toString();
      if (mode.toLowerCase().equals("reset")) {
        forceSwitchState(getSMode((ac_Ac_State[1] + 1) % 2));
      } else if (mode.toLowerCase().equals("mode_switch")) {
        forceCoolerState(getTMode((ac_Ac_State[0] + 1) % 3));
      } else {
        throw new Throwable("Invalid type");
      }
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
