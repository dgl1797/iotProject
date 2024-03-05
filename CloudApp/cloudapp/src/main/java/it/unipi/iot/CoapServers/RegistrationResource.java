package it.unipi.iot.CoapServers;

import java.nio.charset.StandardCharsets;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import it.unipi.iot.DAOs.RegistryDAO;
import it.unipi.iot.Utils.Logger;

public class RegistrationResource extends CoapResource {
  public RegistrationResource() {
    super("register");
    setObservable(false);
  }

  public void handlePOST(CoapExchange exchange) {
    final String msg = exchange.getRequestText();
    final String ipv6 = exchange.getSourceAddress().getHostAddress();

    try {
      final JSONObject json = (JSONObject) JSONValue.parseWithException(msg);
      int id = Integer.parseInt(json.get("id").toString());
      if (!RegistryDAO.registerNode(id, ipv6))
        throw new Throwable("MySQL Failure");
      Response response = new Response(CoAP.ResponseCode.CONTENT);
      response.setPayload("{\"res\":\"success\"}");
      exchange.respond(response);
    } catch (Throwable e) {
      Logger.ERROR("coap", "Failed to parse message");
      e.printStackTrace();
      exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE, "Failed".getBytes(StandardCharsets.UTF_8));
    }
  }
}
