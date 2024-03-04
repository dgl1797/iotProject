package it.unipi.iot.Controllers;

import java.io.IOException;
import java.sql.SQLException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.elements.exception.ConnectorException;

import it.unipi.iot.DAOs.RegistryDAO;
import it.unipi.iot.Utils.Logger;

public final class CoAPSender {

  private static String getNodeName(int nodeId) {
    return nodeId == 1 ? "env" : "mah";
  }

  public static boolean sendCommand(String command, int nodeId, String res) {
    try {
      final String ipv6 = RegistryDAO.getIPv6(nodeId);
      if (ipv6 == null)
        throw new SQLException("ipv6 not registered");
      CoapClient client = new CoapClient(String.format("coap://[%s]/%s", ipv6, res));

      CoapResponse response = client.post(command, MediaTypeRegistry.TEXT_PLAIN);
      if (response.isSuccess()) {
        return true;
      } else {
        Logger.ERROR(getNodeName(nodeId), String.format("Actuator responded with: %s", response));
        return false;
      }
    } catch (ConnectorException | IOException | SQLException e) {
      Logger.ERROR(getNodeName(nodeId), "Couldn't post command");
      e.printStackTrace();
      return false;
    }
  }
}
