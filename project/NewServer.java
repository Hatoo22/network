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
    
    // Map to hold each player's score
    private static Map<String, Integer> scoreboard = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Waiting for players...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                synchronized (connectedPlayers) {
                    connectedPlayers.add(clientHandler);
                }
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Updates all clients with the current connected and waiting lists.
    private static void updateAllClients() {
        String connectedList = "CONNECTED:" + getConnectedPlayerNames();
        String waitingList = "WAITING:" + String.join(",", waitingRoom);
        synchronized (connectedPlayers) {
            for (ClientHandler client : connectedPlayers) {
                client.sendMessage(connectedList);
                client.sendMessage(waitingList);
            }
        }
    }

    // Returns a comma-separated list of connected players.
    private static String getConnectedPlayerNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : connectedPlayers) {
            names.add(client.getPlayerName());
        }
        return String.join(",", names);
    }

    // Starts the game for all players in the waiting room.
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

    // Checks whether the game should start.
    private static synchronized void checkAndStartGame() {
        if (waitingRoom.size() == 4) {
            if (timer != null) {
                timer.cancel();
                isTimerRunning = false;
            }
            startGame();
        } else if (waitingRoom.size() >= 2 && !isTimerRunning) {
            startCountdownTimer();
        }
    }

    // Starts the countdown timer.
    private static void startCountdownTimer() {
        if (isTimerRunning)
            return;
        isTimerRunning = true;
        countdownSeconds = 30;
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Timer: " + countdownSeconds + " seconds remaining");
                synchronized (connectedPlayers) {
                    for (ClientHandler client : connectedPlayers) {
                        client.sendMessage("TIMER:" + countdownSeconds);
                    }
                }
                if (countdownSeconds > 0) {
                    countdownSeconds--;
                } else {
                    timer.cancel();
                    isTimerRunning = false;
                    if (waitingRoom.size() >= 2) {
                        startGame();
                    }
                }
            }
        }, 0, 1000);
    }
    
    // Broadcast the updated scoreboard to all connected clients.
    private static synchronized void broadcastScores() {
        StringBuilder sb = new StringBuilder("SCORES:");
        for (Map.Entry<String, Integer> entry : scoreboard.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        if (sb.length() > 0 && sb.charAt(sb.length()-1) == ',') {
            sb.deleteCharAt(sb.length()-1);
        }
        String scoreMessage = sb.toString();
        synchronized (connectedPlayers) {
            for (ClientHandler client : connectedPlayers) {
                client.sendMessage(scoreMessage);
            }
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String playerName;
        private int score = 0;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getPlayerName() {
            return playerName;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // Ask for player's name
                out.println("ENTER_NAME");
                playerName = in.readLine();
                synchronized (scoreboard) {
                    scoreboard.put(playerName, 0);
                }
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
                    } else if (input.equals("LEAVE")) {
                        break;
                    } else if (input.startsWith("SCORE:")) {
                        try {
                            int newScore = Integer.parseInt(input.substring(6).trim());
                            score = newScore;
                            synchronized (scoreboard) {
                                scoreboard.put(playerName, score);
                            }
                            broadcastScores();
                        } catch (NumberFormatException e) {
                            // Ignore invalid score updates.
                        }
                    }
                    // Process additional messages if needed...
                }
            } catch (IOException e) {
                System.out.println("Connection lost with " + playerName);
            } finally {
                synchronized (connectedPlayers) {
                    connectedPlayers.remove(this);
                    waitingRoom.remove(playerName);
                    updateAllClients();
                    broadcastMessage(playerName + " has left the game.");
                }
                synchronized (scoreboard) {
                    scoreboard.remove(playerName);
                    broadcastScores();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Sends a message to this client.
        public void sendMessage(String message) {
            out.println(message);
        }

        // Broadcasts a message to all connected clients.
        private void broadcastMessage(String message) {
            synchronized (connectedPlayers) {
                for (ClientHandler client : connectedPlayers) {
                    client.sendMessage("PLAYER_LEFT:" + message);
                }
            }
        }
    }
}
