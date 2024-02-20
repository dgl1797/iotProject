package it.unipi.iot.Models;

import java.sql.Timestamp;

public class MachineData {
  private int id;
  private Timestamp date;
  private int temperature;
  private int humidity;
  private int outputs;

  public MachineData(int temperature, int humidity, int outputs) {
    this.temperature = temperature;
    this.humidity = humidity;
    this.outputs = outputs;
  }

  public MachineData(int id, Timestamp date, int temperature, int humidity, int outputs) {
    this.temperature = temperature;
    this.humidity = humidity;
    this.outputs = outputs;
    this.id = id;
    this.date = date;
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

  @Override
  public String toString() {
    return String.format("{\"id\":%d,\"date\":%s,\"temperature\":%d,\"humidity\":%d,\"outputs\":%d}", getId(),
        getDate(), getTemperature(),
        getHumidity(), getOutputs());
  }

}
