package it.unipi.iot.DAOs;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import it.unipi.iot.Config.HikariPoolDataSource;
import it.unipi.iot.Models.EnvironmentData;
import it.unipi.iot.Utils.Logger;

public class EnvironmentDAO {
  static final String tableName = "environment_data";

  static public void createTable() {
    try (Connection conn = HikariPoolDataSource.getConnection()) {
      final String query = String.format(
          "CREATE TABLE IF NOT EXISTS %s(id INT AUTO_INCREMENT, temperature INT NOT NULL, ac_state VARCHAR(30) NOT NULL, date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id));",
          tableName);

      Logger.INFO("cloud", String.format("Creating table %s", tableName));
      Statement stmt = conn.createStatement();
      stmt.execute(query);
      Logger.SUCCESS("cloud", String.format("%s creation Completed", tableName));
    } catch (SQLException e) {
      Logger.ERROR("cloud", String.format("Couldn't create table: %s", tableName));
      e.printStackTrace();
    }
  }

  static public EnvironmentData saveData(EnvironmentData newData) {
    try (Connection conn = HikariPoolDataSource.getConnection()) {
      final String query = String.format("INSERT INTO %s (temperature, ac_state) VALUES (%d, '%s');",
          tableName, newData.getTemperature(), newData.getActuatorState());

      Statement stmt = conn.createStatement();
      int affectedRows = stmt.executeUpdate(query, Statement.RETURN_GENERATED_KEYS);
      if (affectedRows != 1)
        throw new SQLException("Affected rows is not 1");
      ResultSet results = stmt.getGeneratedKeys();
      if (results.next()) {
        int generatedId = results.getInt(1);
        final String retrieveQuery = String.format("SELECT * FROM %s WHERE id=%d", tableName, generatedId);
        results = stmt.executeQuery(retrieveQuery);
        if (results.next()) {
          return new EnvironmentData(results.getInt("id"), results.getTimestamp("date"), results.getInt("temperature"),
              results.getString("ac_state"));
        } else {
          throw new SQLException("Data not saved correctly");
        }
      } else {
        throw new SQLException("Data not saved correctly");
      }
    } catch (SQLException e) {
      Logger.ERROR("cloud", String.format("Couldn't save data: %s", newData));
      e.printStackTrace();
      return null;
    }
  }
}
