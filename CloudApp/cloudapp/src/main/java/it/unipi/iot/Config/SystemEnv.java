package it.unipi.iot.Config;

public final class SystemEnv {
  public final static String DB_STRING = System.getenv("DB_STRING");
  public final static String DB_USER = System.getenv("DB_USER");
  public final static String DB_PASS = System.getenv("DB_PASS");
  public final static String BROKER_URL = System.getenv("BROKER_URL");
}
