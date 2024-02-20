package it.unipi.iot.Models;

import java.sql.Timestamp;

public class EnvironmentData {
  private int id;
  private Timestamp date;
  private int temperature;

  public EnvironmentData(int temperature) {
    this.temperature = temperature;
  }

  public EnvironmentData(int id, Timestamp timestamp, int temperature) {
    this.id = id;
    this.date = timestamp;
    this.temperature = temperature;
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

  @Override
  public String toString() {
    return String.format("{\"id\":%d, \"date\":%s, \"temperature\":%d}", getId(), getDate(), getTemperature());
  }

}