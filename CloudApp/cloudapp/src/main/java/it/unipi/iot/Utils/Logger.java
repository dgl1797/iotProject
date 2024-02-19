package it.unipi.iot.Utils;

public class Logger {
  public static void ERROR(String caller, String message) {
    System.out.println(String.format("\033[0;91m[%s] - %s\033[0m", caller.toUpperCase(), message));
  }

  public static void SUCCESS(String caller, String message) {
    System.out.println(String.format("\033[0;92m[%s] - %s\033[0m", caller.toUpperCase(), message));
  }

  public static void INFO(String caller, String message) {
    System.out.println(String.format("\033[0;93m[%s] - %s\033[0m", caller.toUpperCase(), message));
  }

  public static void NORMAL(String caller, String message) {
    System.out.println(String.format("[%s] - %s", caller.toUpperCase(), message));
  }
}
