package it.unipi.iot.Models;

import java.sql.Timestamp;

public class MachineData {
  private int id;
  private Timestamp date;
  private int temperature;
  private int humidity;
  private int outputs;
  private int actuatorState;

  public MachineData(int temperature, int humidity, int outputs, int actuatorState) {
    this.temperature = temperature;
    this.humidity = humidity;
    this.outputs = outputs;
    this.actuatorState = actuatorState;
  }

  public MachineData(int id, Timestamp date, int temperature, int humidity, int outputs, int actuatorState) {
    this.temperature = temperature;
    this.humidity = humidity;
    this.outputs = outputs;
    this.id = id;
    this.date = date;
    this.actuatorState = actuatorState;
  }

  private int convertStateString(String actuatorState) {
    actuatorState = actuatorState.toLowerCase();
    return actuatorState.equals("off") ? 0 : actuatorState.equals("medium") ? 1 : 2;
  }

  public MachineData(int id, Timestamp date, int temperature, int humidity, int outputs, String actuatorState) {
    this.temperature = temperature;
    this.humidity = humidity;
    this.outputs = outputs;
    this.id = id;
    this.date = date;
    this.actuatorState = convertStateString(actuatorState);
  }

  public int getId() {
    return this.id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public Timestamp getDate() {
    return this.date;
  }

  public void setDate(Timestamp date) {
    this.date = date;
  }

  public int getTemperature() {
    return this.temperature;
  }

  public void setTemperature(int temperature) {
    this.temperature = temperature;
  }

  public int getHumidity() {
    return this.humidity;
  }

  public void setHumidity(int humidity) {
    this.humidity = humidity;
  }

  public int getOutputs() {
    return this.outputs;
  }

  public void setOutputs(int outputs) {
    this.outputs = outputs;
  }

  public String getActuatorState() {
    return actuatorState == 0 ? "off" : actuatorState == 1 ? "medium" : "high";
  }

  @Override
  public String toString() {
    return String.format(
        "{\"id\":%d,\"date\":%s,\"temperature\":%d,\"humidity\":%d,\"outputs\":%d, \"ac_state\":\"%s\"}", getId(),
        getDate(), getTemperature(),
        getHumidity(), getOutputs(), getActuatorState());
  }

}
