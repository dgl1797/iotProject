package it.unipi.iot;

import it.unipi.iot.Config.SystemEnv;
import it.unipi.iot.Controllers.Environment;
import it.unipi.iot.Controllers.Machine;
import it.unipi.iot.DAOs.EnvironmentDAO;
import it.unipi.iot.DAOs.MachineDAO;
import it.unipi.iot.DAOs.RegistryDAO;
import it.unipi.iot.Registry.CoAPServer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.TimeZone;

import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.MqttException;

public class App {
    public static void generate_tables() {
        MachineDAO.createTable();
        EnvironmentDAO.createTable();
        RegistryDAO.createTable();
    }

    public static void main(String[] args)
            throws MqttException, ConnectorException, IOException, SQLException, InterruptedException {

        // Server Setup
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // MQTTs
        Environment env = new Environment(SystemEnv.BROKER_URL);
        Machine mah = new Machine(SystemEnv.BROKER_URL);

        generate_tables();

        // CoAP
        CoAPServer coapServer = new CoAPServer();

        // threads
        Thread envThread = new Thread(env);
        envThread.start();

        Thread mahThread = new Thread(mah);
        mahThread.start();

        Thread servThread = new Thread(coapServer);
        servThread.start();
    }
}
