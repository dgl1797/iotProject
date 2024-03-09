package it.unipi.iot.GUI;

import java.util.Scanner;

import it.unipi.iot.Controllers.Environment;
import it.unipi.iot.Controllers.Machine;
import it.unipi.iot.Utils.Logger;

public class MainMenu implements Runnable {

  private final Environment envControllerRef;
  private final Machine mahControllerRef;

  private final String GUIDE = "*************************************************************************************************\n"
      +
      "*                                       Production Terminal                                     *\n" +
      "*************************************************************************************************\n" +
      "*                                       Utility Commands                                        *\n" +
      "- help -> prints a guide for sending commands to sensors\n" +
      "*                                       Actuators Control                                       *\n" +
      "- econditioner:<state> -> forces the conditioner actuator to passed state [off; heating; cooling]\n" +
      "- mswitch:<state> -> forces the machine switch to passed state [on; off]\n" +
      "- mcooler:<state> -> forces the machine cooler to passed state [off; medium; high]\n" +
      "- release:<actuator> -> releases the state of an actuator that was forced [econditioner; mswitch; mcooler, all]\n";

  public MainMenu(Environment envController, Machine mahController) {
    envControllerRef = envController;
    mahControllerRef = mahController;
  }

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
          String forcedState = command.split(":")[1].strip().toLowerCase();
          if (!envControllerRef.forceState(forcedState))
            Logger.ERROR("menu", "Couldn't force the requested state");
        } else if (command.matches("^mswitch:[a-zA-Z]+$")) {
          String forcedState = command.split(":")[1].strip().toLowerCase();
          if (!mahControllerRef.forceSwitchState(forcedState))
            Logger.ERROR("menu", "Couldn't force the requested state");
        } else if (command.matches("^mcooler:[a-zA-Z]+$")) {
          String forcedState = command.split(":")[1].strip().toLowerCase();
          if (!mahControllerRef.forceCoolerState(forcedState))
            Logger.ERROR("menu", "Couldn't force the requested state");
        } else if (command.matches("^release:[a-zA-Z]+$")) {
          String resource = command.split(":")[1].strip().toLowerCase();
          if (!resource.equals("econditioner") && !resource.equals("mswitch") &&
              !resource.equals("mcooler") && !resource.equals("all")) {
            Logger.ERROR("menu", "Resource doesn't exist");
          } else if (resource.equals("econditioner")) {
            envControllerRef.releaseState();
          } else if (resource.equals("mswitch")) {
            mahControllerRef.releaseSwitch();
          } else if (resource.equals("mcooler")) {
            mahControllerRef.releaseCooler();
          } else if (resource.equals("all")) {
            envControllerRef.releaseState();
            mahControllerRef.releaseSwitch();
            mahControllerRef.releaseCooler();
          }

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
