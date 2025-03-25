package network.project;

import java.io.*;
import java.net.*;
import java.util.*;

public class NewServer {
    private static final int PORT = 12345;
    private static List<ClientHandler> connectedPlayers = new ArrayList<>();
    private static List<String> waitingRoom = new ArrayList<>();
    private static boolean isTimerRunning = false;
    private static Timer timer;
    private static int countdownSeconds = 30;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Waiting for players...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                connectedPlayers.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Updates all clients with the latest player lists
    private static void updateAllClients() {
        String connectedList = "CONNECTED:" + getConnectedPlayerNames();
        String waitingList = "WAITING:" + String.join(",", waitingRoom);

        for (ClientHandler client : connectedPlayers) {
            client.sendMessage(connectedList);
            client.sendMessage(waitingList);
        }
    }

    // Returns the names of connected players
    private static String getConnectedPlayerNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : connectedPlayers) {
            names.add(client.getPlayerName());
        }
        return String.join(",", names);
    }

    // Starts the game for all players in the waiting room
    private static synchronized void startGame() {
        String startMessage = "GAME_STARTED";
        for (String player : waitingRoom) {
            for (ClientHandler client : connectedPlayers) {
                if (client.getPlayerName().equals(player)) {
                    client.sendMessage(startMessage);
                }
            }
        }
        waitingRoom.clear();
        updateAllClients();
    }

    // Checks if the game can start (either by timer or full room)
    private static synchronized void checkAndStartGame() {
        if (waitingRoom.size() == 4) {
            if (timer != null) {
                timer.cancel(); // Stop the timer if it is running
                isTimerRunning = false;
            }
            startGame();
        } else if (waitingRoom.size() >= 2 && !isTimerRunning) {
            startCountdownTimer();
        }
    }

    // Starts the countdown timer
    private static void startCountdownTimer() {
        if (isTimerRunning) return;

        isTimerRunning = true;
        countdownSeconds = 30;

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Timer: " + countdownSeconds + " seconds remaining");

                // Send the timer value to all clients
                for (ClientHandler client : connectedPlayers) {
                    client.sendMessage("TIMER:" + countdownSeconds);
                }

                if (countdownSeconds > 0) {
                    countdownSeconds--; // Decrease the timer
                } else {
                    timer.cancel(); // Stop the timer when it reaches zero
                    isTimerRunning = false;

                    // Start the game if there are enough players
                    if (waitingRoom.size() >= 2) {
                        startGame();
                    }
                }
            }
        }, 0, 1000); // Run the timer every 1 second
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String playerName;
        private int score = 0;


        public ClientHandler(Socket socket) {
            this.socket = socket;
        }public String getPlayerName() {
            return playerName;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("ENTER_NAME");
                playerName = in.readLine();

                synchronized (connectedPlayers) {
                    updateAllClients();
                }

                String input;
                while ((input = in.readLine()) != null) {
                    if (input.equals("READY")) {
                        synchronized (waitingRoom) {
                            if (!waitingRoom.contains(playerName)) {
                                waitingRoom.add(playerName);
                                updateAllClients();
                                checkAndStartGame();
                            }
                        }
                    }
                    if (input.equals("CORRECT_ANSWER")) { 
    score += 10;
    sendMessage("SCORE_UPDATED:" + score);

    if (score >= 20) {
        broadcastMessage(playerName + " has won the game!");
    }
}

                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (connectedPlayers) {
                    connectedPlayers.remove(this);
                    waitingRoom.remove(playerName);
                    updateAllClients();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
private void broadcastMessage(String message) {
    for (ClientHandler client : connectedPlayers) {
        client.sendMessage(message);
    }
}

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}