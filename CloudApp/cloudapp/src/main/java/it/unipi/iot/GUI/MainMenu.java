package it.unipi.iot.GUI;

import java.util.Scanner;

import it.unipi.iot.Utils.Logger;

public class MainMenu implements Runnable {

  private final String GUIDE = "*************************************************************************************************\n"
      +
      "*                                       Production Terminal                                     *\n" +
      "*************************************************************************************************\n" +
      "*                                       Utility Commands                                        *\n" +
      "- help -> prints a guide for sending commands to sensors\n" +
      "*                                       Actuators Control                                       *\n" +
      "- econditioner:<state> -> forces the conditioner actuator to passed state [off; heating; cooling]\n" +
      "- mswitch:<state> -> forces the machine switch to passed state [on; off]\n" +
      "- mcooler:<state> -> forces the machine cooler to passed state [off; medium; high]\n";

  @Override
  public void run() {

    System.out.println(GUIDE);
    String command;

    try (Scanner getInput = new Scanner(System.in)) {
      while (true) {
        command = getInput.nextLine().toLowerCase().strip().replaceAll(" ", "");
        if (command.equals("help")) {
          System.out.println(GUIDE);
        } else if (command.matches("^econditioner:[a-zA-Z]+$")) {
          // conditioner control:
          String forcedState = command.split(":")[1].strip();
          Logger.SUCCESS("menu", String.format("forced conditioner to %s", forcedState));
        } else if (command.matches("^mswitch:[a-zA-Z]+$")) {
          String forcedState = command.split(":")[1].strip();
          Logger.SUCCESS("menu", String.format("forced machine switch to %s", forcedState));
        } else if (command.matches("^mcooler:[a-zA-Z]+$")) {
          String forcedState = command.split(":")[1].strip();
          Logger.SUCCESS("menu", String.format("forced machine cooler to %s", forcedState));
        } else {
          Logger.ERROR("menu", String.format("Unrecognized command: %s", command));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

  }

}
