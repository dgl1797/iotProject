package it.unipi.iot.CoAP;

import org.eclipse.californium.core.CoapServer;

import it.unipi.iot.Utils.Logger;

public class CoAPServer extends CoapServer implements Runnable {
  private static CoAPServer server;

  public void createRegistrationServer() {
    try {
      Logger.INFO("coap", "Starting registration server ...");
      server = new CoAPServer();
      server.add(new RegistrationResource());
      server.start();
      Logger.SUCCESS("coap", "CoAP Registration server listening...");
    } catch (Exception e) {
      Logger.ERROR("coap", "An unexpected error occurred during server startup.");
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    createRegistrationServer();
  }
}