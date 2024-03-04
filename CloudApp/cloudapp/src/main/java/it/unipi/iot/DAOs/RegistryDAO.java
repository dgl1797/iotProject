package it.unipi.iot.DAOs;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import it.unipi.iot.Config.HikariPoolDataSource;
import it.unipi.iot.Utils.Logger;

public class RegistryDAO {
  static final String tableName = "nodes_registry";

  static public void createTable() {
    try {
      final String query = String.format(
          "CREATE TABLE IF NOT EXISTS %s(id INT AUTO_INCREMENT, ipv6 VARCHAR(70) NOT NULL, PRIMARY KEY(id));",
          tableName);

      Logger.INFO("cloud", String.format("Creating table %s", tableName));
      Connection conn = HikariPoolDataSource.getConnection();
      Statement stmt = conn.createStatement();
      stmt.execute(query);
      Logger.SUCCESS("cloud", String.format("%s creation Completed", tableName));
    } catch (SQLException e) {
      Logger.ERROR("Server", String.format("Couldn't create table: %s", tableName));
      e.printStackTrace();
    }
  }

  static public boolean registerNode(int id, String ipv6) {
    final String nodeExists = String.format("SELECT * FROM %s WHERE id=%d", tableName, id);
    final String insertNode = String.format("INSERT INTO %s (id, ipv6) VALUES (%d, \"%s\")", tableName, id, ipv6);
    final String updateNode = String.format("UPDATE %s SET ipv6=\"%s\" WHERE id=%d", tableName, ipv6, id);

    try (Connection conn = HikariPoolDataSource.getConnection()) {
      // CHECK IF EXISTS
      Statement stmt = conn.createStatement();
      ResultSet resset = stmt.executeQuery(nodeExists);
      if (!resset.next()) {
        // INSERT NEW ENTRY
        int affectedRows = stmt.executeUpdate(insertNode);
        if (affectedRows != 1)
          throw new SQLException("Invalid update operation");
      } else {
        // UPDATE NODE'S IPV6
        int affectedRows = stmt.executeUpdate(updateNode);
        if (affectedRows != 1)
          throw new SQLException("Invalid update operation");
      }
      Logger.SUCCESS("coap", String.format("Registered node: %d - %s", id, ipv6));
      return true;
    } catch (SQLException e) {
      Logger.ERROR("coap", String.format("Failed to register node: %d - %s", id, ipv6));
      e.printStackTrace();
      return false;
    }
  }

  static public String getIPv6(int nodeId) {
    final String query = String.format("SELECT ipv6 FROM %s WHERE id=%d", tableName, nodeId);
    try (Connection conn = HikariPoolDataSource.getConnection()) {
      Statement stmt = conn.createStatement();
      ResultSet rset = stmt.executeQuery(query);
      if (!rset.next())
        throw new SQLException("Not Registered");
      return rset.getString("ipv6");
    } catch (SQLException e) {
      Logger.ERROR("coap", String.format("Node ID: %d not registered", nodeId));
      e.printStackTrace();
      return null;
    }
  }
}
