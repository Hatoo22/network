/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package network.project;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // Consider Concurrent Collections for thread safety

public class NewServer {
    private static final int PORT = 12345;
    // Using synchronizedList for thread-safe list access
    private static List<ClientHandler> connectedPlayers = Collections.synchronizedList(new ArrayList<>());
    private static List<String> waitingRoom = Collections.synchronizedList(new ArrayList<>());
    private static List<String> gamePlayers = Collections.synchronizedList(new ArrayList<>()); // Players currently in game
    private static final int MIN_PLAYERS_TO_CONTINUE = 2;
    private static final int MAX_PLAYERS_FOR_GAME = 4; // Added for clarity

    private static boolean isTimerRunning = false;
    private static Timer timer; // Timer for waiting room countdown
    private static int countdownSeconds = 30;

    // Using ConcurrentHashMap for thread-safe map access
    private static Map<String, Integer> scoreboard = new ConcurrentHashMap<>();

    // Flag to indicate if a game is currently active
    private static volatile boolean isGameActive = false; // Use volatile as it's accessed by multiple threads

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT + ". Waiting for players...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("ServerLog: New client connected: " + clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                connectedPlayers.add(clientHandler); // Add to list of all connected clients
                new Thread(clientHandler).start(); // Start a new thread for the client
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcasts current connected and waiting lists to all connected clients
    private static void updateAllClients() {
        String connectedListMsg = "CONNECTED:" + getConnectedPlayerNames();
        String waitingListMsg = "WAITING:" + String.join(",", waitingRoom);

        // Iterate over a copy to avoid ConcurrentModificationException if a client disconnects
        List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
        for (ClientHandler client : currentConnectedPlayers) {
            client.sendMessage(connectedListMsg);
            client.sendMessage(waitingListMsg);
        }
    }

    // Gets names of all currently connected players
    private static String getConnectedPlayerNames() {
        List<String> names = new ArrayList<>();
        // Iterate over a copy for safety
        List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
        for (ClientHandler client : currentConnectedPlayers) {
            // Only add if player name is set (client finished initial name entry)
            if (client.getPlayerName() != null) {
                names.add(client.getPlayerName());
            }
        }
        return String.join(",", names);
    }

    // Starts the game with players from the waiting room
    private static synchronized void startGame() {
        if (isGameActive) {
            System.out.println("ServerLog: Game is already active. Cannot start a new one.");
            return; // Prevent starting a new game if one is already running
        }
        isGameActive = true; // Set game state to active
        System.out.println("ServerLog: Starting game with players in waiting room: " + waitingRoom);

        // Move players from waiting room to game players list
        gamePlayers.clear(); // Clear previous game players
        gamePlayers.addAll(waitingRoom); // Add all from waiting room
        waitingRoom.clear(); // Clear the waiting room

        // Initialize scores for players in the game
        for (String player : gamePlayers) {
            scoreboard.put(player, 0); // Set initial score to 0
        }

        // Notify players that the game has started
        String startMessage = "GAME_STARTED";
        // Iterate over a copy for safety
        List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
        for (ClientHandler client : currentConnectedPlayers) {
            if (gamePlayers.contains(client.getPlayerName())) {
                client.sendMessage(startMessage); // Send game start message to players in the game
            }
        }

        // Broadcast initial scores (all are 0)
        broadcastScores();
        // Update connected/waiting lists on all clients
        updateAllClients();

        System.out.println("ServerLog: Game started.");
    }

    // Handles ending the game prematurely if players leave or game cannot continue
    private static synchronized void endGamePrematurely() {
         System.out.println("ServerLog: endGamePrematurely called.");
         if (!isGameActive) {
             System.out.println("ServerLog: No game active to end prematurely. Returning.");
             return; // Only end if a game is active
         }
         isGameActive = false; // Set game state to inactive
         System.out.println("ServerLog: Game ending prematurely. Current gamePlayers size: " + gamePlayers.size());

         String endMessageText;
         if (gamePlayers.size() == 1) {
             // Exactly one player remains - they win by default
             String winnerName = gamePlayers.get(0);
             Integer winnerScore = scoreboard.get(winnerName); // Get their current score
             System.out.println("ServerLog: Only one player left. Winner: " + winnerName + ", Score: " + winnerScore);
             endMessageText = winnerName + " wins by default as all other players left! Final Score: " + winnerScore + " points.";
         } else if (gamePlayers.isEmpty()) {
             // All players left
             System.out.println("ServerLog: All players left.");
             endMessageText = "Game ended because all players left. No winner.";
         } else {
             // More than one player left, but not enough to continue
              System.out.println("ServerLog: Not enough players (" + gamePlayers.size() + ") to continue.");
              endMessageText = "Game ended due to insufficient players remaining.";
         }

         // Construct the full message including final scores before cleanup
         String fullEndMessage = "GAME_ENDED:" + endMessageText + " Final Scores: " + getFormattedScores();

         System.out.println("ServerLog: Broadcasting GAME_ENDED message (premature): " + fullEndMessage);

         // Notify all players (including the winner if applicable)
         // Iterate over a copy for safety
         List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
         for (ClientHandler client : currentConnectedPlayers) {
              client.sendMessage(fullEndMessage);
         }


         // Reset game state on the server
         gamePlayers.clear();
         scoreboard.clear();

         // Update client GUIs after game ends
         updateAllClients();
         broadcastScores(); // Broadcast empty scores

         System.out.println("ServerLog: Premature game end processed.");
    }


    // Checks if game should start based on waiting room size and timer
    private static synchronized void checkAndStartGame() {
        if (isGameActive) {
             System.out.println("ServerLog: Game is already active. Skipping checkAndStartGame.");
             return; // Don't check or start if game is active
        }

        if (waitingRoom.size() >= MAX_PLAYERS_FOR_GAME) {
            // If max players reached, start game immediately and cancel timer
            if (timer != null) {
                timer.cancel();
                isTimerRunning = false;
                System.out.println("ServerLog: Timer cancelled. Max players reached.");
            }
            startGame();
        } else if (waitingRoom.size() >= MIN_PLAYERS_TO_CONTINUE && !isTimerRunning) {
            // If min players reached and timer is not running, start timer
            startCountdownTimer();
        }
        // If less than MIN_PLAYERS_TO_CONTINUE, do nothing or stop timer if running (optional)
         if (waitingRoom.size() < MIN_PLAYERS_TO_CONTINUE && isTimerRunning) {
             if (timer != null) {
                 timer.cancel();
                 isTimerRunning = false;
                 System.out.println("ServerLog: Timer cancelled. Not enough players.");
                  // Notify clients timer stopped? Optional
             }
         }
    }

    // Starts the countdown timer for game start
    private static void startCountdownTimer() {
        if (isTimerRunning) return; // Prevent starting timer if already running
        isTimerRunning = true;
        countdownSeconds = 30; // Reset countdown time
        System.out.println("ServerLog: Countdown timer started: " + countdownSeconds + " seconds.");

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                 // Check flag inside task to ensure it stops if game starts/ends via other means
                 if (!isTimerRunning || isGameActive) {
                     timer.cancel();
                     System.out.println("ServerLog: Timer task cancelled due to flag or game active.");
                     return;
                 }

                // Broadcast timer update to all connected clients
                List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
                for (ClientHandler client : currentConnectedPlayers) {
                    client.sendMessage("TIMER:" + countdownSeconds);
                }
                System.out.println("ServerLog: Timer: " + countdownSeconds + " seconds remaining"); // Server console log

                if (countdownSeconds > 0) {
                    countdownSeconds--; // Decrement time
                } else {
                    // Timer reached zero
                    timer.cancel(); // Stop the timer
                    isTimerRunning = false; // Reset timer flag
                    System.out.println("ServerLog: Countdown timer finished.");

                    // Check player count again before starting
                    if (waitingRoom.size() >= MIN_PLAYERS_TO_CONTINUE) {
                         startGame(); // Start game if enough players
                    } else {
                         System.out.println("ServerLog: Timer finished, but not enough players to start game.");
                          // Notify clients that game didn't start? Optional
                    }
                }
            }
        }, 0, 1000); // Start immediately, repeat every 1000ms (1 second)
    }

    // Broadcasts current scores of players in the game
    private static synchronized void broadcastScores() {
        String scoreMessage = "SCORES:" + getFormattedScores();

        List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
        for (ClientHandler client : currentConnectedPlayers) {
             client.sendMessage(scoreMessage);
        }
    }

    // Helper method to format scores string from the current scoreboard
     private static String getFormattedScores() {
         StringBuilder sb = new StringBuilder();
         boolean first = true;
         for (Map.Entry<String, Integer> entry : scoreboard.entrySet()) {
             if (!first) {
                 sb.append(",");
             }
             sb.append(entry.getKey()).append(":").append(entry.getValue());
             first = false;
         }
         return sb.toString();
     }


    // Handles the logic when a player finishes the game (completes all questions)
     private static synchronized void handlePlayerFinishedGame(String playerName) {
         System.out.println("ServerLog: " + playerName + " has finished the game (completed all questions).");

         if (!isGameActive) {
             System.out.println("ServerLog: Game is not active. Ignoring GAME_FINISHED from " + playerName);
             return; // Only proceed if a game is active
         }

         // End the game immediately when the first player finishes
         endGameDueToFinish();
     }

     // Handles the game ending specifically when a player finishes all questions
     private static synchronized void endGameDueToFinish() {
         System.out.println("ServerLog: endGameDueToFinish called.");
         if (!isGameActive) {
             System.out.println("ServerLog: No game active to end due to finish. Returning.");
             return; // Only end if a game is active
         }
         isGameActive = false; // Set game state to inactive
         System.out.println("ServerLog: Game ending because a player finished all questions.");


         // Calculate the winner(s) based on highest score among players who were in the game
         String winnerInfoText = calculateWinnerInfo();

         // Construct the game over message
         String fullEndMessage = "GAME_ENDED:" + winnerInfoText;

         System.out.println("ServerLog: Broadcasting GAME_ENDED message (player finished): " + fullEndMessage);


         // Notify all players
          List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
          for (ClientHandler client : currentConnectedPlayers) {
              client.sendMessage(fullEndMessage);
          }


         // Reset game state on the server
         gamePlayers.clear();
         scoreboard.clear(); // Clear scoreboard for the next game

         // Update client GUIs after game ends
         updateAllClients();
         broadcastScores(); // Broadcast empty scores

         System.out.println("ServerLog: Game ended (player finished). Winner info sent: " + winnerInfoText);

     }

     // Calculates the winner(s) based on the current scoreboard for game completion scenario
     private static synchronized String calculateWinnerInfo() {
         System.out.println("ServerLog: Calculating winner info based on scores. Scoreboard size: " + scoreboard.size());
         if (scoreboard.isEmpty()) {
             return "No players participated or scored.";
         }

         int maxScore = -1;
         List<String> winners = new ArrayList<>();

         System.out.println("ServerLog: Current Scoreboard: " + scoreboard);
         for (int score : scoreboard.values()) {
             if (score > maxScore) {
                 maxScore = score;
             }
         }
          System.out.println("ServerLog: Max score found: " + maxScore);

         for (Map.Entry<String, Integer> entry : scoreboard.entrySet()) {
             if (entry.getValue() == maxScore) {
                 winners.add(entry.getKey());
             }
         }
         System.out.println("ServerLog: Winners found: " + winners);

         StringBuilder winnerInfo = new StringBuilder();
         if (winners.size() == 1) {
             winnerInfo.append(winners.get(0)).append(" wins with ").append(maxScore).append(" points!");
         } else {
             winnerInfo.append("It's a tie between: ").append(String.join(" and ", winners))
                       .append(" with ").append(maxScore).append(" points each!");
         }

         winnerInfo.append(". Final Scores: ").append(getFormattedScores());
          System.out.println("ServerLog: Formatted winner info: " + winnerInfo.toString());


         return winnerInfo.toString();
     }


    // Inner class to handle each client connection
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String playerName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getPlayerName() {
            return playerName;
        }

        // Method to send a message to this specific client
        public void sendMessage(String message) {
             System.out.println("ServerLog: Sending message to " + playerName + ": " + message);
            if (out != null && !socket.isClosed()) {
                out.println(message);
            } else {
                System.out.println("ServerLog: Attempted to send message to closed socket for " + playerName);
            }
        }


        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                 sendMessage("ENTER_NAME");
                 playerName = in.readLine();
                 if (playerName == null || playerName.trim().isEmpty()) {
                     playerName = "UnknownPlayer" + (int)(Math.random() * 1000);
                 }
                 System.out.println("ServerLog: Client " + socket + " set name to " + playerName);

                 scoreboard.put(playerName, 0); // Add to scoreboard upon connection

                updateAllClients();


                String input;
                while ((input = in.readLine()) != null) {
                    System.out.println("ServerLog: Received from " + playerName + ": " + input);

                    if ("READY".equals(input)) {
                         synchronized (waitingRoom) {
                             // Prevent joining waiting room if a game is active
                             if (isGameActive) {
                                 System.out.println("ServerLog: " + playerName + " attempted to join waiting room while game active.");
                                 sendMessage("SERVER_MESSAGE:A game is currently active. Please wait for it to finish."); // Inform client
                             } else if (!waitingRoom.contains(playerName) && !gamePlayers.contains(playerName)) {
                                 waitingRoom.add(playerName);
                                 System.out.println("ServerLog: " + playerName + " joined waiting room. Current waiting: " + waitingRoom.size());
                                 updateAllClients();
                                 checkAndStartGame();
                             } else {
                                 // Player is already in waiting or game
                                  System.out.println("ServerLog: " + playerName + " is already in waiting or game.");
                             }
                         }
                    } else if ("LEAVE".equals(input)) {
                        System.out.println("ServerLog: " + playerName + " requested to leave.");
                        break;
                    } else if (input.startsWith("UPDATE_SCORE:")) {
                         if (isGameActive && gamePlayers.contains(playerName)) {
                             try {
                                 int newScore = Integer.parseInt(input.substring(13).trim());
                                 scoreboard.put(playerName, newScore);
                                 System.out.println("ServerLog: Score updated for " + playerName + ": " + newScore);
                                 broadcastScores();
                             } catch (NumberFormatException e) {
                                 System.err.println("ServerLog: Invalid score format received from " + playerName + ": " + input);
                             }
                         } else {
                              System.out.println("ServerLog: Received score update from player not in active game: " + playerName);
                         }
                    } else if ("GAME_FINISHED".equals(input)) {
                         handlePlayerFinishedGame(playerName);
                    } else {
                          System.out.println("ServerLog: Unknown command from " + playerName + ": " + input);
                     }
                }
            } catch (IOException e) {
                System.out.println("ServerLog: Connection lost with " + playerName + (playerName != null ? "" : " (before name set)") + ": " + e.getMessage());
            } finally {
                handleDisconnect();
            }
        }

        private void handleDisconnect() {
            System.out.println("ServerLog: Cleaning up connection for " + playerName);
            connectedPlayers.remove(this);

            synchronized (waitingRoom) {
                waitingRoom.remove(playerName);
            }

            boolean wasInGame = gamePlayers.remove(playerName);

            // Remove the player's score from the scoreboard
             System.out.println("ServerLog: Removing " + playerName + " from scoreboard.");
            scoreboard.remove(playerName);

            String leaveMessage = "PLAYER_LEFT:" + (playerName != null ? playerName : "Unknown");
             List<ClientHandler> currentConnectedPlayers = new ArrayList<>(connectedPlayers);
             for (ClientHandler client : currentConnectedPlayers) {
                 client.sendMessage(leaveMessage);
             }

            // If the player who left *was* in the game, check if the game should now end prematurely
            if (wasInGame && isGameActive) {
                 System.out.println("ServerLog: Player " + playerName + " left during active game. Checking if game should end prematurely.");
                 System.out.println("ServerLog: Current gamePlayers size after removal: " + gamePlayers.size());
                 if (gamePlayers.size() < MIN_PLAYERS_TO_CONTINUE) {
                      endGamePrematurely();
                 }
            } else if (wasInGame && !isGameActive) {
                System.out.println("ServerLog: Player " + playerName + " left, they were in game but game was not active.");
            }

            updateAllClients();
            broadcastScores();

            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
                System.out.println("ServerLog: Resources closed for " + playerName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
