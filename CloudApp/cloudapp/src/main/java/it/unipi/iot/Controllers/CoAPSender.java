package it.unipi.iot.Controllers;

import java.io.IOException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.elements.exception.ConnectorException;

import it.unipi.iot.DAOs.RegistryDAO;
import it.unipi.iot.Utils.Logger;

public class CoAPSender {
  private CoapClient client;
  private int nodeId;

  public CoAPSender(int nodeId, String... res) {
    this.nodeId = nodeId;
    String ipv6 = RegistryDAO.getIPv6(nodeId);
    client = new CoapClient(String.format("coap://[%s]/%s", ipv6, String.join("/", res)));
  }

  private String getNodeName() {
    return this.nodeId == 1 ? "env" : "mah";
  }

  public boolean sendCommand(String command) {
    try {
      CoapResponse response = client.post(command, MediaTypeRegistry.TEXT_PLAIN);
      if (response.isSuccess()) {
        return true;
      } else {
        Logger.ERROR(getNodeName(), String.format("Actuator responded with: %s", response));
        return false;
      }
    } catch (ConnectorException | IOException e) {
      Logger.ERROR(getNodeName(), "Couldn't post command");
      e.printStackTrace();
      return false;
    }
  }
}
