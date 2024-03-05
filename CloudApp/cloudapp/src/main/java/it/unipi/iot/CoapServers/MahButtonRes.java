package it.unipi.iot.CoapServers;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

import it.unipi.iot.Controllers.Machine;

public class MahButtonRes extends CoapResource {
  private final Machine mahRef;

  public MahButtonRes(Machine mahRef) {
    super("mahbutton");
    setObservable(false);
    this.mahRef = mahRef;
  }

  public void handlePOST(CoapExchange exchange) {
    mahRef.handlePOST(exchange);
  }

}
