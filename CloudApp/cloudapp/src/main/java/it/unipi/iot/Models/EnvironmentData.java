package it.unipi.iot.Models;

import java.sql.Timestamp;

public class EnvironmentData {
  private int id;
  private Timestamp date;
  private int temperature;
  private int actuatorState;

  public EnvironmentData(int temperature, int actuatorState) {
    this.temperature = temperature;
    this.actuatorState = actuatorState;
  }

  public EnvironmentData(int id, Timestamp timestamp, int temperature, int actuatorState) {
    this.id = id;
    this.date = timestamp;
    this.temperature = temperature;
    this.actuatorState = actuatorState;
  }

  private int convertStateString(String stateString) {
    stateString = stateString.toLowerCase();
    return stateString.equals("off") ? 0 : stateString.equals("heating") ? 1 : 2;
  }

  public EnvironmentData(int id, Timestamp timestamp, int temperature, String actuatorState) {
    this.id = id;
    this.date = timestamp;
    this.temperature = temperature;
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

  public String getActuatorState() {
    return this.actuatorState == 0 ? "off" : this.actuatorState == 1 ? "heating" : "cooling";
  }

  public void setActuatorState(int actuatorState) {
    this.actuatorState = actuatorState;
  }

  @Override
  public String toString() {
    return String.format("{\"id\":%d, \"date\":%s, \"temperature\":%d, \"ac_state\":\"%s\"}", getId(), getDate(),
        getTemperature(), getActuatorState());
  }

}