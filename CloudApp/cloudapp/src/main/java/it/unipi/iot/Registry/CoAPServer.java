package it.unipi.iot.Registry;

import org.eclipse.californium.core.CoapServer;

import it.unipi.iot.Controllers.Environment;
import it.unipi.iot.Controllers.Machine;
import it.unipi.iot.Utils.Logger;

public class CoAPServer extends CoapServer implements Runnable {
  private static CoAPServer registryServer;
  private static CoAPServer buttonServer;

  private Environment envRef = null;
  private Machine mahRef = null;

  public void createRegistrationServer() {
    try {
      Logger.INFO("coap", "Starting registration server ...");
      registryServer = new CoAPServer();
      registryServer.add(new RegistrationResource());
      registryServer.start();
      Logger.SUCCESS("coap", "CoAP Registration server listening...");
    } catch (Exception e) {
      Logger.ERROR("coap", "An unexpected error occurred during server startup.");
      e.printStackTrace();
    }
  }

  public void createButtonServer() {
    try {
      Logger.INFO("coap", "Starting Buttons Listener...");
      buttonServer = new CoAPServer();
      buttonServer.add(envRef);
      buttonServer.add(mahRef);
      buttonServer.start();
      Logger.SUCCESS("coap", "CoAP Button server listening...");
    } catch (Exception e) {
      Logger.ERROR("coap", "Unexpected error occurred during button server startup.");
      e.printStackTrace();
    }
  }

  public void setResources(Environment envRes, Machine mahRes) {
    envRef = envRes;
    mahRef = mahRes;
  }

  @Override
  public void run() {
    try {
      if (envRef == null || mahRef == null)
        throw new Exception("Impossible to start CoAP Server: Resources have not been set");
      createRegistrationServer();
      createButtonServer();
    } catch (Exception e) {
      Logger.ERROR("coap", e.getMessage());
      e.printStackTrace();
    }
  }
}