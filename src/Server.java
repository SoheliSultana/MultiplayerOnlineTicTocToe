import com.jcraft.jsch.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.io.Console;

public class Server {
    /**
     * The main() function runs both at the user local and remote machines.
     *
     * @param args args[0] receives a file name of remote servers
     */
    public static void main(String[] args) {
        if (args.length == 1)
            new Server(args[0]); // master server to run locally
        else if (args.length == 0)
            new Server();         // slave servers to run remotely
        else {
            // invalid arguments
            System.err.println(
                    "usage: java -cp jsch-0.1.54.jar:. JSpace.Server config.txt");
            System.exit(-1);
        }
    }

    /**
     * This is the constructor executed at a user-local machine.
     *
     * @param configFile a configuration file name. It must include:
     *                   user account (1st line); password (2nd line);
     *                   and all remote host IP names (the rest).
     */
    public Server(String configFile) {
        final int JschPort = 22;      // Jsch IP port

        // Reading keyboard inputs
        Scanner keyboard = new Scanner(System.in);
        String username = null;
        String password = null;
        try {
            // read the user name from the console                                                            
            System.out.print("User: ");
            username = keyboard.nextLine();

            // read the password from the console                                                             
            Console console = System.console();
            password = new String(console.readPassword("Password: "));

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        // Read configFile to initialize username, password, and hosts
        BufferedReader fileReader = null;
        ArrayList<String> hosts = new ArrayList<String>();
        try {
            // Open a file that lists all remote servers
            fileReader
                    = new BufferedReader(new InputStreamReader
                    (new BufferedInputStream
                            (new FileInputStream
                                    (new File(configFile)))));
            // Read remote host IPs
            while (fileReader.ready())
                hosts.add(fileReader.readLine());
            fileReader.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        // A command to launch remotely:
        //          java -cp ./jsch-0.1.54.jar:. JSpace.Server
        String cur_dir = System.getProperty("user.dir");
        String command
                = "java -cp " + cur_dir + "/jsch-0.1.54.jar:" + cur_dir +
                " Server";

        // establish an ssh2 connection to each remote machine and run
        // JSpace.Server there.
        Connection connections[] = new Connection[hosts.size()];
        for (int i = 0; i < hosts.size(); i++) {
            // System.out.println( username + "@" + hosts.get( i ) + " to run: " + command );
            connections[i] = new Connection(username, password,
                    hosts.get(i), command);
        }

        // the main body of the master server
        try {
            // LAB 3: Have the master server read a message from each of slave
            // print out the messges from all slaves
            for (int i = 0; i < hosts.size(); i++) {
                String msg = (String) connections[i].in.readObject();
                System.out.println(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        // close the ssh2 connection with each remote machine
        for (int i = 0; i < hosts.size(); i++) {
            connections[i].close();
        }
    }

    /**
     * This is the constructor executed at each remote machine.
     */
    public Server() {
        // receive an ssh2 connection from a user-local master server.
        Connection connection = new Connection();

        // the main body of the slave server
        try {
            // LAB 3: Have a slave send a message: Hello from Slave at IP addr
            String msg = "Hello from Slave at " + InetAddress.getLocalHost().getHostName();
            connection.out.writeObject(msg);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
