import java.net.*;
import java.io.*;

public class P2P {
    private final int INTERVAL = 1000;  // 1 second

    // Constructor: creates just only one connection with my counterpart.
    public P2P(InetAddress addr, int port, String message) {

        // Prepare a server socket and make it non-blocking
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            // ITEM 1: set the server non-blocking, (i.e. time out beyon 1000) 
            server.setSoTimeout(1000);

        } catch (Exception e) {
            error(e);
        }

        // While accepting a remote request, try to send my connection request
        Socket client = null;
        while (true) {
            try {
                // ITEM 2: Try to accept a connection as a server
                client = server.accept();
            } catch (SocketTimeoutException ste) {
                // Couldn't receive a connection request withtin INTERVAL
            } catch (IOException ioe) {
                error(ioe);
            }
            // Check if a connection was established. If so, leave the loop
            if (client != null)
                break;

            try {
                // ITEM 3: Try to request a connection as a client
                client = new Socket(addr, port);
            } catch (IOException ioe) {
                // Connection refused
            }
            // Check if a connection was established, If so, leave the loop
            if (client != null)
                break;
        }

        // Exchange a message with my counter part.
        try {
            System.out.println("TCP connection established...");
            ObjectOutputStream output
                    = new ObjectOutputStream(client.getOutputStream());// ITEM 4: Create an ObjectOutputStream object
            ObjectInputStream input
                    = new ObjectInputStream(client.getInputStream());// ITEM 5: Create an InputOutputStream Object
            output.writeObject(message + " from " +
                    InetAddress.getLocalHost().getHostName());
            System.out.println((String) input.readObject());
        } catch (Exception e) {
            error(e);
        }
    }

    // Print out an error message and terminate the program
    private static void error(Exception e) {
        e.printStackTrace();
        System.exit(-1);
    }

    // Usage: java P2P ipAddr port
    public static void main(String[] args) {
        try {
            // validate arguments
            InetAddress addr = InetAddress.getByName(args[0]);
            int port = Integer.parseInt(args[1]);

            // Start the application
            new P2P(addr, port, args[2]);
        } catch (Exception e) {
            System.err.println("Usage: java P2P ipAddr port message");
            System.exit(-1);
        }
    }
}
