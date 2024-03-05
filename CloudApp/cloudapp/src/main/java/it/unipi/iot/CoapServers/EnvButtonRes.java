package it.unipi.iot.CoapServers;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;
import it.unipi.iot.Controllers.Environment;

public class EnvButtonRes extends CoapResource {
  private final Environment envRef;

  public EnvButtonRes(Environment envRef) {
    super("envbutton");
    setObservable(false);
    this.envRef = envRef;
  }

  public void handlePOST(CoapExchange exchange) {
    envRef.handlePOST(exchange);
  }
}
