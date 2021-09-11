import com.jcraft.jsch.*;
import com.jcraft.jsch.JSchException;

import java.awt.event.WindowEvent;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.*;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.io.Console;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.swing.*;
import java.io.*;


/**
 * @author Soheli Sultana
 */
public class OnlineTicTacToe implements ActionListener {
    private final int INTERVAL = 1000; // 1 second
    private final int NBUTTONS = 12; // #bottons
    private InputStream input = null; // input from my counterpart
    private DataOutputStream output = null; // output from my counterpart
    private JFrame window = null; // the tic-tac-toe window
    private JButton[] button = new JButton[NBUTTONS]; // button[0] - button[9]
    private boolean[] myTurn = new boolean[1]; // T: my turn, F: your turn
    private String myMark = null; // "O" or "X"
    private String yourMark = null; // "X" or "O"
    List<Integer> listd1 = Arrays.asList(new Integer[]{0, 4, 8});
    List<Integer> listd2 = Arrays.asList(new Integer[]{2, 4, 6});
    private static Set<Integer> diag1 = new HashSet();
    private static Set<Integer> diag2 = new HashSet();
    private Map<String, Integer> stateMap = new HashMap<>();
    private static String[] keys = new String[]{"r0", "r1", "r2", "c0", "c1", "c2", "d1", "d2"};
    private Set<Integer> selectedButton = new HashSet<>();
    private Map<String, Integer> stateMapAuto = new HashMap<>();

    /**
     * Prints out the usage.
     */
    private static void usage() {
        System.err.println("Usage: java OnlineTicTacToe ipAddr ipPort(>=5000) [auto]");
        System.exit(-1);
    }

    /**
     * Prints out the track trace upon a given error and quits the application.
     *
     * @param an exception
     */
    private static void error(Exception e) {
        e.printStackTrace();
        System.exit(-1);
    }

    /**
     * Starts the online tic-tac-toe game.
     *
     * @param args[0]: my counterpart's ip address, args[1]: his/her port, (arg[2]: "auto")
     *                 if args.length == 0, this Java program is remotely launched by JSCH.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            // if no arguments, this process was launched through JSCH
            try {
                OnlineTicTacToe game = new OnlineTicTacToe();
            } catch (IOException e) {
                error(e);
            }
        } else {
            // this process was launched from the user console.
            // verify the number of arguments
            if (args.length != 2 && args.length != 3) {
                System.err.println("args.length = " + args.length);
                usage();
            }
            // verify the correctness of my counterpart address
            InetAddress addr = null;
            try {
                addr = InetAddress.getByName(args[0]);
            } catch (UnknownHostException e) {
                error(e);
            }
            // verify the correctness of my counterpart port
            int port = 0;
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                error(e);
            }
            if (port < 5000) {
                usage();
            }
            // check args[2] == "auto"
            if (args.length == 3 && args[2].equals("auto")) {
                // auto play
                OnlineTicTacToe game = new OnlineTicTacToe(args[0]);
            } else {
                // interactive play
                OnlineTicTacToe game = new OnlineTicTacToe(addr, port);
            }
        }
    }

    /**
     * Is the constructor that is remote invoked by JSCH. It behaves as a server.
     * The constructor uses a Connection object for communication with the client.
     * It always assumes that the client plays first.
     */
    public OnlineTicTacToe() throws IOException {
        // receive an ssh2 connection from a user-local master server.
        Connection connection = new Connection();
        input = connection.in;
        output = connection.out;
        // for debugging, always good to write debugging messages to the local file
        // don't use System.out that is a connection back to the client.
        PrintWriter logs = new PrintWriter(new FileOutputStream("logs.txt"));
        logs.println("Auto play: got started.");
        logs.flush();
        myMark = "X"; // auto player is always the 2nd.
        yourMark = "O";
        // the main body of auto play.

        diag1.addAll(listd1);
        diag2.addAll(listd2);
        // updating count for rows, columns and diagonals
        for (String key : keys) {
            stateMap.put(key, 0);
            stateMapAuto.put(key, 0);
        }

        HashSet<Integer> set = new HashSet<>();
        Random rand = new Random();

        while (true) {
            // busy waiting, untill get input from local user
            while (input.available() <= 0) {
                int count = 0;
                count++;
                count = count % 10;
            }

            byte[] bytes = new byte[1024];
            // read local user actions
            int length = input.read(bytes);
            String str = new String(bytes, 0, length, StandardCharsets.UTF_8);

            if (str.equals("NewGame")) {
                clearState();
                logs.println("starting a new game...");
                logs.flush();
                continue;
            }

            if (str.equals("ExitGame")) {
                logs.println("Quit the game...");
                logs.flush();
                connection.close();

            }
            logs.println("received message = " + str);
            logs.flush();
            String[] msg = str.split(" ");
            int buttonId = Integer.parseInt(msg[0]);

            selectedButton.add(buttonId);

            if (selectedButton.size() == 9) {
                continue;
            }
            // update counts for rows, columns and diagonals
            // based on local user action
            updateStateByLocalPlayer(buttonId);
            // get automated player next movement
            int n = getAutoPlayerNextMove();
            // update counts for rows, columns and diagonals
            // based on new movement of automated player
            updateStateByAutoPlayer(n);
            selectedButton.add(n);
            String msg2 = n + " " + myMark;
            output.writeBytes(msg2);
            output.flush();
            logs.println("next movement of auto player" + " " + msg2);
            logs.flush();
        }
    }

    /**
     * Is the constructor that, upon receiving the "auto" option,
     * launches a remote OnlineTicTacToe through JSCH. This
     * constructor always assumes that the local user should play
     * first. The constructor uses a Connection object for
     * communicating with the remote process.
     *
     * @param my auto counter part's ip address
     */
    public OnlineTicTacToe(String hostname) {
        final int JschPort = 22; // Jsch IP port
        // Read username, password, and a remote host from keyboard
        Scanner keyboard = new Scanner(System.in);
        String username = null;
        String password = null;
        // The JSCH establishment process is pretty much the same as Lab3.
        // establish an ssh2 connection to ip and run
        // Server there.
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

        // A command to launch remotely:
        //          java -cp ./jsch-0.1.54.jar:. JSpace.Server
        String cur_dir = System.getProperty("user.dir");
        String command
                = "java -cp " + cur_dir + "/jsch-0.1.54.jar:" + cur_dir +
                " OnlineTicTacToe";
        Connection connection = null;
        try {
            connection = new Connection(username, password,
                    hostname, command);
            // the main body of the master server
            input = connection.in;
            output = connection.out;
            // set up a window
            makeWindow(true); // I'm a former
            // start my counterpart thread
            Counterpart counterpart = new Counterpart();
            counterpart.start();
        } catch (Exception e) {
            connection.close();
        }
    }

    /**
     * Is the constructor that sets up a TCP connection with my counterpart,
     * brings up a game window, and starts a slave thread for listenning to
     * my counterpart.
     *
     * @param my counterpart's ip address
     * @param my counterpart's port
     */
    public OnlineTicTacToe(InetAddress addr, int port) {
        // set up a TCP connection with my counterpart

        boolean playingOnSameMachine = false;

        try {
            Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
            for (; n.hasMoreElements(); ) {
                NetworkInterface e = n.nextElement();
                Enumeration<InetAddress> a = e.getInetAddresses();
                for (; a.hasMoreElements(); ) {
                    InetAddress addr2 = a.nextElement();
                    if (addr2.getHostAddress().equals(addr.getHostAddress())) {
                        playingOnSameMachine = true;
                    }
                }
            }
        } catch (Exception e) {

        }

        ServerSocket server = null;
        boolean workAsClient = false;
        boolean workAsServer = false;
        try {
            // first connected will act as server
            server = new ServerSocket(port);
            server.setSoTimeout(INTERVAL);
            workAsServer = true;
        } catch (BindException e) {
            // as port already bind, then the second connected one will act as client
            workAsClient = true;
        } catch (IOException e) {

        }
        Socket client = null;
        Boolean success = false;

        if (playingOnSameMachine) {
            // if players are in same machine
            while (true) {
                try {
                    // first connection will act as a server
                    if (workAsServer) {
                        client = server.accept();
                    }

                } catch (Exception ioe) {
                    //  error(ioe);
                }
                // Check if a connection was established. If so, leave the loop
                if (client != null) {
                    success = true;
                    break;
                }


                try {
                    //Try to request a connection as a client
                    if (workAsClient) {
                        client = new Socket(addr, port);
                    }
                } catch (IOException ioe) {
                    // Connection refused
                }
                // Check if a connection was established, If so, leave the loop
                if (client != null) {
                    success = false;

                    break;
                }

            }
        } else {
            // if players are in two different machine
            while (true) {
                try {

                    client = server.accept();

                } catch (Exception ioe) {
                    //  error(ioe);
                }
                // Check if a connection was established. If so, leave the loop
                if (client != null) {
                    success = true;
                    break;
                }


                try {
                    //Try to request a connection as a client
                    client = new Socket(addr, port);
                } catch (IOException ioe) {
                    // Connection refused
                }
                // Check if a connection was established, If so, leave the loop
                if (client != null) {
                    success = false;

                    break;
                }

            }
        }

        try {

            input = new DataInputStream(client.getInputStream());
            output = new DataOutputStream(client.getOutputStream());
        } catch (IOException ioe) {
            //error(ioe);
        }
        // set up a window
        makeWindow(success); // or makeWIndow( false );
        // start my counterpart thread
        Counterpart counterpart = new Counterpart();
        counterpart.start();
    }

    /**
     * Creates a 3x3 window for the tic-tac-toe game
     *
     * @param true if this window is created by the former, (i.e., the
     *             person who starts first. Otherwise false.
     */
    private void makeWindow(boolean amFormer) {
        myTurn[0] = amFormer;
        myMark = (amFormer) ? "O" : "X"; // 1st person uses "O"
        yourMark = (amFormer) ? "X" : "O"; // 2nd person uses "X"
        // create a window
        window = new JFrame("OnlineTicTacToe(" +
                ((amFormer) ? "former)" : "latter)") + myMark);
        window.setSize(300, 300);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLayout(new GridLayout(4, 3));
        // initialize all 12 cells.
        for (int i = 0; i < NBUTTONS; i++) {
            button[i] = new JButton();
            if (i >= 9) {
                switch (i) {
                    case 9:
                        button[i].setText("New Game");
                        break;
                    case 10:
                        button[i].setText("Switch Turn");
                        break;
                    case 11:
                        button[i].setText("Exit Game");
                        break;
                    default:
                        break;
                }

            }
            button[i].addActionListener(this);
            window.add(button[i]);
        }
        // make it visible
        window.setVisible(true);
    }

    /**
     * Marks the i-th button with mark ("O" or "X")
     *
     * @param the  i-th button
     * @param a    mark ( "O" or "X" )
     * @param true if it has been marked in success
     */
    private boolean markButton(int i, final String mark) {
        if (button[i].getText().equals("")) {
            button[i].setText(mark);
            button[i].setEnabled(false);

            return true;
        }
        return false;
    }

    /**
     * Checks which button has been clicked
     *
     * @param an event passed from AWT
     * @return an integer (0 through to 8) that shows which button has been
     * clicked. -1 upon an error.
     */
    private int whichButtonClicked(ActionEvent event) {
        for (int i = 0; i < NBUTTONS; i++) {
            if (event.getSource() == button[i])
                return i;
        }
        return -1;
    }

    /**
     * Checks if the i-th button has been marked with mark( "O" or "X" ).
     *
     * @param the i-th button
     * @param a   mark ( "O" or "X" )
     * @return true if the i-th button has been marked with mark.
     */
    private boolean buttonMarkedWith(int i, String mark) {
        return button[i].getText().equals(mark);
    }

    /**
     * Pops out another small window indicating that mark("O" or "X") won!
     *
     * @param a mark ( "O" or "X" )
     */
    private void showWon(String mark) {
        JOptionPane.showMessageDialog(null, mark + " won!");
    }

    /**
     * Is called by AWT whenever any button has been clicked. You have to:
     * <ol>
     * <li> check if it is my turn,
     * <li> check which button was clicked with whichButtonClicked( event ),
     * <li> mark the corresponding button with markButton( buttonId, mark ),
     * <li> send this informatioin to my counterpart,
     * <li> checks if the game was completed with
     * buttonMarkedWith( buttonId, mark )
     * <li> shows a winning message with showWon( )
     */
    public void actionPerformed(ActionEvent event) {
        try {
            int i = whichButtonClicked(event);
            if (i < 9) {
                if (myTurn[0] == true) {
                    markButton(i, myMark);
                    String msg = i + " " + myMark;
                    // write local users action, pressed buttonID with mark
                    output.write(msg.getBytes());
                    output.flush();
                    // make my turn disable until the counterpart plays
                    myTurn[0] = false;

                    // after every movement check whether it makes winning
                    // if wins, show the winning message
                    if (checkWinning(myMark)) {
                        showWon(myMark);
                    }
                }
            } else {
                // additional new features, asking for new games, asking for
                // switching turn and asking for exit games
                switch (i) {
                    case 9:
                        output.write("NewGame".getBytes());
                        output.flush();
                        reset(false);
                        break;
                    case 10:
                        output.write("SwitchTurn".getBytes());
                        output.flush();
                        reset(true);
                        break;
                    case 11:
                        output.write("ExitGame".getBytes());
                        output.flush();

                        output.close();
                        input.close();
                        System.exit(-1);
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException ioe) {
            error(ioe);
        }
    }

    /**
     * this method reset the window while players agree for a new game and
     * switching the players turn
     */

    void reset(boolean switchTurn) {

        for (int i = 0; i < NBUTTONS; i++) {
            if (i >= 9) {
                switch (i) {
                    case 9:
                        button[i].setText("New Game");
                        break;
                    case 10:
                        button[i].setText("Switch Turn");
                        break;
                    case 11:
                        button[i].setText("Exit Game");
                        break;
                    default:
                        break;
                }

            } else {
                button[i].setText("");
                button[i].setEnabled(true);
            }


        }
        // asking for switching turn will make the player who started the
        // game last time first, this time that player would be second player.
        if (switchTurn) {
            if (myMark.equals("O")) {
                myMark = "X";
                myTurn[0] = false;
            } else {
                myMark = "O";
                myTurn[0] = true;
            }
        } else {
            if (myMark.equals("O")) {
                myTurn[0] = true;
            } else {
                myTurn[0] = false;
            }
        }


    }

    /**
     * for each player movement, this method invokes other method return true if the player win,
     * otherwise returns false
     */
    boolean checkWinning(String mark) {
        return checkrow(mark) || checkcolumn(mark) || checkDiagonal1(mark) || checkDiagonal2(mark);

    }

    /**
     * check if any rows contain the given marks to be winning
     */

    boolean checkrow(String mark) {
        for (int i = 0; i < 7; i += 3) {
            int count = 0;
            for (int j = i; j < i + 3; j++) {
                if (!buttonMarkedWith(j, mark)) {
                    break;
                } else {
                    count++;
                }
            }
            if (count == 3) {
                return true;
            }

        }
        return false;
    }

    /**
     * check if any columns contain the given marks to be winning
     */
    boolean checkcolumn(String mark) {
        for (int i = 0; i < 3; i++) {
            int count = 0;
            for (int j = i; j < i + 7; j += 3) {

                if (!buttonMarkedWith(j, mark)) {
                    break;
                } else {
                    count++;
                }
            }
            if (count == 3) {
                return true;
            }
        }

        return false;
    }

    /**
     * check if diagonal 1 contains the given marks to be winning
     */
    boolean checkDiagonal1(String mark) {
        for (int i = 0; i < 9; i += 4) {
            if (!buttonMarkedWith(i, mark)) {
                return false;
            }
        }

        return true;
    }

    /**
     * check if diagonal 2 contains the given marks to be winning
     */
    boolean checkDiagonal2(String mark) {
        for (int i = 2; i < 7; i += 2) {
            if (!buttonMarkedWith(i, mark)) {
                return false;
            }
        }

        return true;
    }

    /**
     * This is a reader thread that keeps reading from and behaving as my
     * counterpart.
     */
    private class Counterpart extends Thread {
        /**
         * Is the body of the Counterpart thread.
         */
        @Override
        public void run() {

            while (true) {
                try {
                    // read opposite player's movement
                    while (input.available() <= 0) {
                        int i = 100;
                    }

                    byte[] bytes = new byte[1024];
                    int length = input.read(bytes);
                    String str = new String(bytes, 0, length, StandardCharsets.UTF_8);
                    if (str.equals("NewGame")) {
                        reset(false);
                    } else if (str.equals("SwitchTurn")) {
                        reset(true);
                    } else if (str.equals("ExitGame")) {
                        input.close();
                        output.close();
                        System.exit(-1);
                    } else {
                        String[] msg = str.split(" ");
                        int i = Integer.parseInt(msg[0]);
                        String marks = msg[1];
                        // reflects opposites action in own OnlineTicTacToe window
                        markButton(i, marks);
                        myTurn[0] = true;
                        // check weather opposite players action
                        // makes a winning situation or not
                        if (checkWinning(marks)) {
                            showWon(marks);
                        }
                    }
                } catch (IOException ioe) {
                    error(ioe);
                }
            }
        }
    }
    //  Intelligent Automated player's code

    /**
     * PART of AUTOMATED Player, to make the automated player intelligent.
     * Update the all rows, columns and diagonals count based
     * on the auto player movement
     *
     * @param buttonIndex button id that choosen by auto player for next movement
     */

    void updateStateByAutoPlayer(int buttonIndex) {
        String rowKey = "r" + buttonIndex / 3;
        String colKey = "c" + buttonIndex % 3;

        stateMap.put(rowKey, -1);
        if (stateMapAuto.get(rowKey) != -1) {
            stateMapAuto.put(rowKey, stateMapAuto.get(rowKey) + 1);
        }
        stateMap.put(colKey, -1);

        if (stateMapAuto.get(colKey) != -1) {
            stateMapAuto.put(colKey, stateMapAuto.get(colKey) + 1);
        }


        if (diag1.contains(buttonIndex)) {
            stateMap.put("d1", -1);
            if (stateMapAuto.get("d1") != -1) {
                stateMapAuto.put("d1", stateMapAuto.get("d1") + 1);
            }
        }
        if (diag2.contains(buttonIndex)) {
            stateMap.put("d2", -1);
            if (stateMapAuto.get("d2") != -1) {
                stateMapAuto.put("d2", stateMapAuto.get("d2") + 1);
            }
        }

    }

    /***
     * PART OF AUTOMATED player, to make the automated player intelligent
     * Update the all rows, columns and diagonals count
     * based on local user movement
     * @param buttonIndex button id that pressed by local user
     */
    void updateStateByLocalPlayer(int buttonIndex) {
        String rowKey = "r" + buttonIndex / 3;
        String colKey = "c" + buttonIndex % 3;

        if (stateMap.get(rowKey) != -1) {
            stateMap.put(rowKey, stateMap.get(rowKey) + 1);
        }
        stateMapAuto.put(rowKey, -1);

        if (stateMap.get(colKey) != -1) {
            stateMap.put(colKey, stateMap.get(colKey) + 1);
        }
        stateMapAuto.put(colKey, -1);


        if (diag1.contains(buttonIndex) && stateMap.get("d1") != -1) {
            stateMap.put("d1", stateMap.get("d1") + 1);
        }

        stateMapAuto.put("d1", -1);

        if (diag2.contains(buttonIndex) && stateMap.get("d2") != -1) {
            stateMap.put("d2", stateMap.get("d2") + 1);
        }
        stateMapAuto.put("d2", -1);

    }

    /***
     * This method finds the best position for auto player's
     * next action, that will prevent the local user to win
     * or will make auto player win based on the
     * @return the button number that would the auto player next action
     */
    int getAutoPlayerNextMove() {

        // if button 4 is not pressed by local user
        // then auto player will press button 4 first
        // that position reduce the chance of winning for local user
        if (!selectedButton.contains(4)) {
            return 4;
        }
        // otherwise it will select another button
        // based on the high probabilty of auto player
        // winning, other wise it will choose a button that
        // will prevent local user to win
        int count = -1;
        String keyIndex = null;
        String autoKey = null;

        for (String key : stateMap.keySet()) {
            if (stateMap.get(key) > count) {
                count = stateMap.get(key);
                keyIndex = key;
            }

            if (stateMapAuto.get(key) == 2) {
                autoKey = key;
            }
        }

        if (autoKey != null) {
            return getKeyButtonIndex(autoKey);
        } else {
            return getKeyButtonIndex(keyIndex);
        }


    }

    int getKeyButtonIndex(String keyIndex) {
        if (keyIndex.startsWith("r")) {
            int i = Integer.parseInt(keyIndex.split("r")[1]);
            int row = i * 3;
            for (int j = row; j < row + 3; j++) {
                if (!selectedButton.contains(j)) {
                    return j;
                }
            }
        }

        if (keyIndex.startsWith("c")) {
            int i = Integer.parseInt(keyIndex.split("c")[1]);

            for (int j = i; j < i + 7; j += 3) {
                if (!selectedButton.contains(j)) {
                    return j;
                }
            }
        }

        if (keyIndex.equals("d1")) {
            for (Integer i : diag1) {
                if (!selectedButton.contains(i)) {
                    return i;
                }
            }
        }

        if (keyIndex.equals("d2")) {
            for (Integer i : diag2) {
                if (!selectedButton.contains(i)) {
                    return i;
                }
            }
        }

        return -1;
    }

    void clearState() {
        selectedButton.clear();
        for (String key : keys) {
            stateMap.put(key, 0);
            stateMapAuto.put(key, 0);
        }
    }

}

