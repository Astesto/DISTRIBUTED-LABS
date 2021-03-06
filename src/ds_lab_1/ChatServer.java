package ds_lab_1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A multithreaded chat room server. When a client connects the server requests a screen name by sending the client the text "SUBMITNAME", and keeps requesting a name until a unique one is received. After a client submits a unique name, the server acknowledges with "NAMEACCEPTED". Then all messages from that client will be broadcast to all other clients that have submitted a unique screen name. The broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple chat server, there are a few features that have been left out. Two are very useful and belong in production code:
 *
 * @author SAUDI VICTOR ABUBAKAR
 * 
 * 1. The protocol should be enhanced so that the client can send clean disconnect messages to the server.
 *
 * 2. The server should do some logging.
 */
public class ChatServer {

    /**
     * The port that the server listens on.
     */
    private static final int PORT = 9001;

    /**
     * The set of all names of clients in the chat room. Maintained so that we can check that new clients are not registering name already in use.
     */
    private static HashSet<String> names = new HashSet<String>();

    /**
     * The set of all the print writers for all the clients. This set is kept so we can easily broadcast messages.
     */
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
    private static HashMap<String, PrintWriter> writerMap = new HashMap<String, PrintWriter>();

    /**
     * The appplication main method, which just listens on a port and spawns handler threads.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    /**
     * A handler thread class. Handlers are spawned from the listening loop and are responsible for a dealing with a single client and broadcasting its messages.
     */
    private static class Handler extends Thread {

        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, squirreling away the socket. All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a screen name until a unique one has been submitted, then acknowledges the name and registers the output stream for the client in a global set, then repeatedly gets inputs and broadcasts them.
         */
        @Override
        public void run() {
            try {

                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client.  Keep requesting until
                // a name is submitted that is not already used.  Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (names) {
                        if (!names.contains(name)) {
                            System.out.println("New Client Added: " + name);
                            names.add(name);
                            writerMap.put(name, out);
                            break;
                        }
                    }
                }

                // Now that a successful name has been chosen, add the
                // socket's print writer to the set of all writers so
                // this client can receive broadcast messages.
                out.println("NAMEACCEPTED");
                writers.add(out);

                //Now we must populate the ComboBoxes in client environments with the name of all the clients
                updateClients();
                
                // Accept messages from this client and broadcast them.
                // Ignore other clients that cannot be broadcasted to.
                while (true) {
                    String input = in.readLine();
                    if (isUnicastMessage(input)) {
                        System.out.println("UNICAST MESSAGE");
                        writerMap.get(name).println("MESSAGE " + name + ": " + input.replace(getUnicastRecipent(input) + ">>", ""));
                        writerMap.get(getUnicastRecipent(input)).println("MESSAGE " + name + ": " + input.replace(getUnicastRecipent(input) + ">>", ""));
                    } else {
                        for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + name + ": " + input);
                            System.out.println("MESSAGE " + name + ": " + input);
                        }
                    }
                    if (input == null) {
                        return;
                    }

                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                // This client is going down!  Remove its name and its print
                // writer from the sets, and close its socket.
                writerMap.remove(name);
                if (name != null) {
                    names.remove(name);
                }
                if (out != null) {
                    writers.remove(out);
                    updateClients();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        //Check if a message is intended for a specific client
        public boolean isUnicastMessage(String message) {
            for (String clientName : names) {
                String clientPrefix = clientName + ">>";
                if (message.contains(clientPrefix)) {
                    return true;
                }
            }
            return false;
        }

        //Extract only the message from a unicast message
        public String getUnicastRecipent(String message) {
            String unicastClient = null;
            for (String clientName : names) {
                String clientPrefix = clientName + ">>";
                if (message.contains(clientPrefix)) {
                    unicastClient = clientName;
                    break;
                }
            }
            return unicastClient;
        }

        //Removes specified client from the list
        public void removeClient(String clientName) {
            writerMap.remove(clientName);
            System.out.println("Client Removed: " + clientName);
        }

        //Returns a list of clients to the client side
        public static String getAllClients() {
            String[] keys = writerMap.keySet().toArray(new String[0]);
            String clientArray = "All";
            for (int i = 0; i < keys.length; i++) {
                clientArray = clientArray + "," + keys[i];
            }
            return clientArray;
        }

        //Updates client list when a new client is added or removed
        public void updateClients() {
            for (PrintWriter writer : writers) {
                writer.println("CLIENTLIST" + getAllClients());
            }
        }
    }
}
