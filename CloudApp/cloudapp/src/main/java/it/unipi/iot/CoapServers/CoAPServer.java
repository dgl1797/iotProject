package it.unipi.iot.CoapServers;

import org.eclipse.californium.core.CoapServer;

import it.unipi.iot.Controllers.Environment;
import it.unipi.iot.Controllers.Machine;
import it.unipi.iot.Utils.Logger;

public class CoAPServer extends CoapServer implements Runnable {
  private static CoAPServer server;

  private Environment envRef = null;
  private Machine mahRef = null;

  public void createCoapServer() {
    try {
      Logger.INFO("coap", "Starting Coap server ...");
      server = new CoAPServer();
      server.add(new RegistrationResource());
      server.add(new EnvButtonRes(envRef));
      server.add(new MahButtonRes(mahRef));
      server.start();
      Logger.SUCCESS("coap", "CoAP server listening...");
    } catch (Exception e) {
      Logger.ERROR("coap", "An unexpected error occurred during server startup.");
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
      createCoapServer();
    } catch (Exception e) {
      Logger.ERROR("coap", e.getMessage());
      e.printStackTrace();
    }
  }
}