package it.unipi.iot.Config;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import it.unipi.iot.Utils.Logger;

public class HikariPoolDataSource {
  private static HikariConfig hikariConfig = new HikariConfig();
  private static HikariDataSource ds = new HikariDataSource();

  static {
    Logger.INFO("cloud", "Initializing connection pool");
    hikariConfig.setJdbcUrl(SystemEnv.DB_STRING);
    hikariConfig.setUsername(SystemEnv.DB_USER);
    hikariConfig.setPassword(SystemEnv.DB_PASS);

    hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
    hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    hikariConfig.setMaximumPoolSize(100);
    ds = new HikariDataSource(hikariConfig);
  }

  public static Connection getConnection() throws SQLException {
    return ds.getConnection();
  }

  public static void deleteConnection(Connection conn) {
    ds.evictConnection(conn);
  }

  private HikariPoolDataSource() {
  }
}