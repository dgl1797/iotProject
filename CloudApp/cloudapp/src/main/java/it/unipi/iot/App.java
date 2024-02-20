package it.unipi.iot;

import it.unipi.iot.Config.SystemEnv;
import it.unipi.iot.DAOs.EnvironmentDAO;
import it.unipi.iot.DAOs.MachineDAO;
import it.unipi.iot.MQTTHandler.Environment;
import it.unipi.iot.MQTTHandler.Machine;
import it.unipi.iot.Utils.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.TimeZone;

import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.MqttException;

public class App {
    public static void generate_tables() {
        MachineDAO.createTable();
        EnvironmentDAO.createTable();
    }

    public static void main(String[] args)
            throws MqttException, ConnectorException, IOException, SQLException, InterruptedException {

        // Server Setup
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Logger.NORMAL("cloud", System.getenv("DB_STRING"));

        // MQTTs
        Environment env = new Environment(SystemEnv.BROKER_URL);
        Machine mah = new Machine(SystemEnv.BROKER_URL);

        generate_tables();

        // threads
        Thread envThread = new Thread(env);
        envThread.start();

        Thread mahThread = new Thread(mah);
        mahThread.start();

        System.out.println("\n");
        Logger.SUCCESS("cloud", "Server Listening...");
    }
}
