package it.unipi.iot.DAOs;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import it.unipi.iot.Config.HikariPoolDataSource;
import it.unipi.iot.Utils.Logger;

public class MachineDAO {
  static final String tableName = "machine_data";

  static public void createTable() {
    try {
      final String query = String.format(
          "CREATE TABLE IF NOT EXISTS %s(id INT AUTO_INCREMENT, temperature INT NOT NULL, humidity INT NOT NULL, quantity INT NOT NULL, date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id));",
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
}
