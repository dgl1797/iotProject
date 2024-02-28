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
  private String ipv6;
  private int nodeId;

  public CoAPSender(int nodeId) {
    this.nodeId = nodeId;
    ipv6 = RegistryDAO.getIPv6(nodeId);
    client = new CoapClient();
  }

  private String getNodeName() {
    return this.nodeId == 1 ? "env" : "mah";
  }

  public void sendCommand(String resource, String command) {
    Logger.INFO(getNodeName(), String.format("Sending Command to %s", ipv6));
    client.setURI(String.format("coap://[%s]%s", ipv6, resource));
    try {
      CoapResponse response = client.post(command, MediaTypeRegistry.TEXT_PLAIN);
      if (response.isSuccess()) {
        Logger.SUCCESS(getNodeName(), String.format("Actuator responded with: %s", response));
      } else {
        Logger.ERROR(getNodeName(), String.format("Actuator responded with: %s", response));
      }
    } catch (ConnectorException | IOException e) {
      Logger.ERROR(getNodeName(), "Couldn't post command");
      e.printStackTrace();
    }
  }
}
