/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package network.project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.util.*;

public class Client extends JFrame {
    private DefaultListModel<String> connectedListModel;
    private DefaultListModel<String> waitingListModel;
    private DefaultListModel<String> gamePlayersListModel;
    private JList<String> connectedList;
    private JList<String> waitingList;
    private JList<String> gamePlayersList;
    private JButton playButton;
    private JButton leaveButton; // New leave button
    private JPanel connectedPanel;
    private JPanel waitingPanel;
    private JPanel gamePanel; // The main panel for the game itself
    private JLabel binaryLabel;
    private JTextField inputField;
    private JButton submitButton;
    private JLabel messageLabel; // Message label used across panels
    private JLabel timerLabel; // Timer label for waiting room countdown
    private JLabel scoreLabel; // Score label (not used in current game panel)
    private HashSet<String> allConnectedPlayers = new HashSet<>();
    private PrintWriter out;
    private BufferedReader in;
    private int currentStage = 0; // Start from index 0
    private int score = 0;
    private javax.swing.Timer questionTimer; // Timer for individual question
    private int questionTimeLeft;
    private JLabel questionTimerLabel; // Label for question timer

    private String[] binaryStages = {"1010", "0011", "11000", "100110", "110110"}; // 5 stages

    // Flag to prevent clearing message after game ends
    private boolean isGameOver = false;

    // Enum to track the current UI state
    private enum UIState { CONNECTED, WAITING, GAME }
    private UIState currentState = UIState.CONNECTED;


    public Client() {
        setTitle("Game Client");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        // Connected players panel
        connectedPanel = new JPanel(new BorderLayout());
        connectedPanel.setBorder(BorderFactory.createTitledBorder("Connected Players"));
        connectedListModel = new DefaultListModel<>();
        connectedList = new JList<>(connectedListModel);
        connectedList.setFont(new Font("Arial", Font.PLAIN, 18));
        connectedPanel.add(new JScrollPane(connectedList), BorderLayout.CENTER);
        // Add message label to the connected panel
        connectedPanel.add(messageLabel, BorderLayout.NORTH); // Initially add message label here

        // Button panel for connected screen
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        playButton = new JButton("Play");
        playButton.setFont(new Font("Arial", Font.BOLD, 20));
        playButton.addActionListener((ActionEvent e) -> {
            if (out != null) {
                out.println("READY");
                // Only switch if not already in waiting or game
                 if(currentState == UIState.CONNECTED) {
                     switchToWaitingRoom();
                 } else {
                     System.out.println("ClientLog: Ignoring Play button click, already in state: " + currentState);
                      // Optional: clear any previous message and show a new one
                       SwingUtilities.invokeLater(() -> {
                            if (!isGameOver) { // Don't overwrite game over message
                                 System.out.println("ClientLog: Attempting to set messageLabel (already ready/in game): Please wait for the current game to finish.");
                                messageLabel.setText("Please wait for the current game to finish.");
                                 // Clear message after a delay
                                 new javax.swing.Timer(5000, event -> {
                                      if (!isGameOver && (currentState == UIState.CONNECTED || currentState == UIState.WAITING)) {
                                           System.out.println("ClientLog: Attempting to clear messageLabel (already ready/in game timer): \"\"");
                                          messageLabel.setText("");
                                      }
                                 }).start();
                            }
                       });
                 }
            }
        });

        leaveButton = new JButton("Leave");
        leaveButton.setFont(new Font("Arial", Font.BOLD, 20));
        leaveButton.addActionListener((ActionEvent e) -> {
            leaveGame();
        });

        buttonPanel.add(playButton);
        buttonPanel.add(leaveButton);
        connectedPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Waiting room panel
        waitingPanel = new JPanel(new BorderLayout());
        waitingPanel.setBorder(BorderFactory.createTitledBorder("Waiting Room"));
        waitingListModel = new DefaultListModel<>();
        waitingList = new JList<>(waitingListModel);
        waitingList.setFont(new Font("Arial", Font.PLAIN, 18));
        waitingPanel.add(new JScrollPane(waitingList), BorderLayout.CENTER);

        timerLabel = new JLabel("Timer: Not started", SwingConstants.CENTER); // Timer for game start countdown
        timerLabel.setFont(new Font("Arial", Font.BOLD, 24));
        waitingPanel.add(timerLabel, BorderLayout.NORTH); // Add timer label to waiting panel

        // Add leave button to waiting panel
        JButton leaveWaitingButton = new JButton("Leave Waiting Room");
        leaveWaitingButton.setFont(new Font("Arial", Font.BOLD, 20));
        leaveWaitingButton.addActionListener(e -> leaveGame());
        waitingPanel.add(leaveWaitingButton, BorderLayout.SOUTH);


        // Initial view is the connected panel
        add(connectedPanel, BorderLayout.CENTER);

        // Connect to the server on startup
        connectToServer();

        setVisible(true);
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true); // Auto-flush
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println("ClientLog: Received from server: " + serverMessage); // For debugging

                        if (serverMessage.equals("ENTER_NAME")) {
                            String playerName = JOptionPane.showInputDialog(this, "Enter your name:");
                            if (playerName == null || playerName.trim().isEmpty()) {
                                playerName = "Player" + (int)(Math.random() * 1000);
                            }
                            out.println(playerName);
                        } else if (serverMessage.startsWith("CONNECTED:")) {
                            updateConnectedList(serverMessage.substring(10));
                        } else if (serverMessage.startsWith("WAITING:")) {
                            updateWaitingList(serverMessage.substring(8));
                        } else if (serverMessage.startsWith("TIMER:")) {
                             // Only update timer label if currently in WAITING state
                             if (currentState == UIState.WAITING) {
                                 String timeRemaining = serverMessage.substring(6);
                                 SwingUtilities.invokeLater(() ->
                                     timerLabel.setText("Game starts in: " + timeRemaining + " seconds")
                                 );
                             } else {
                                System.out.println("ClientLog: Ignoring TIMER message, not in WAITING state. Current state: " + currentState);
                             }
                        } else if (serverMessage.equals("GAME_STARTED")) {
                            SwingUtilities.invokeLater(this::switchToGame);
                        } else if (serverMessage.startsWith("SCORES:")) {
                            updateGamePlayersList(serverMessage.substring(7));
                        } else if (serverMessage.startsWith("PLAYER_LEFT:")) {
                            String playerName = serverMessage.substring(12);
                            SwingUtilities.invokeLater(() -> {
                                // Display player left message based on current state
                                if (!isGameOver) { // Only show if game is not over
                                    String message = playerName + " has left the game.";
                                    // Display message on the appropriate panel's message label
                                    if (currentState == UIState.CONNECTED || currentState == UIState.WAITING) {
                                         // Assuming messageLabel is added to connected/waiting panels
                                         System.out.println("ClientLog: Attempting to set messageLabel in PLAYER_LEFT handler (pre-game): " + message);
                                         messageLabel.setText(message);
                                         // Clear message after a delay if in pre-game state
                                         new javax.swing.Timer(7000, e -> {
                                             if (!isGameOver && (currentState == UIState.CONNECTED || currentState == UIState.WAITING)) {
                                                  System.out.println("ClientLog: Attempting to clear messageLabel in PLAYER_LEFT timer (pre-game): \"\"");
                                                 messageLabel.setText("");
                                             } else {
                                                 System.out.println("ClientLog: PLAYER_LEFT timer skipped clearing messageLabel because game is over or state changed.");
                                             }
                                         }).start();
                                    } else if (currentState == UIState.GAME && gamePanel != null) {
                                        // If in game, update the message label specific to the game panel
                                        // Assuming messageLabel is added to gameContent within gamePanel
                                         System.out.println("ClientLog: Attempting to set messageLabel in PLAYER_LEFT handler (in-game): " + message);
                                         messageLabel.setText(message); // Update game panel message label
                                         // Decide if you want to clear in-game messages automatically
                                         // For now, leave in-game messages until overwritten or game ends
                                    } else {
                                         System.out.println("ClientLog: Ignoring PLAYER_LEFT message display due to unknown state or null panel.");
                                    }
                                } else {
                                    System.out.println("ClientLog: Ignoring PLAYER_LEFT message as game is over.");
                                }
                            });
                        }
                         else if (serverMessage.startsWith("GAME_ENDED:")) {
                            String winnerInfo = serverMessage.substring(11);
                             System.out.println("ClientLog: GAME_ENDED received. Winner info: " + winnerInfo);
                            SwingUtilities.invokeLater(() -> showGameOver(winnerInfo));
                            if (questionTimer != null) {
                                 questionTimer.stop();
                             }
                             if (inputField != null) inputField.setEnabled(false);
                             if (submitButton != null) submitButton.setEnabled(false);
                        }
                        // Handle general messages from the server
                        else if (serverMessage.startsWith("SERVER_MESSAGE:")) {
                             String msg = serverMessage.substring(15);
                             SwingUtilities.invokeLater(() -> {
                                 // Display server message on the currently active panel's message label
                                 if (!isGameOver) { // Don't overwrite game over message
                                     if (currentState == UIState.CONNECTED || currentState == UIState.WAITING) {
                                          System.out.println("ClientLog: Attempting to set messageLabel in SERVER_MESSAGE handler (pre-game): " + msg);
                                         messageLabel.setText(msg);
                                          // Optional: Clear after a delay
                                          // new javax.swing.Timer(7000, e -> messageLabel.setText("")).start();
                                     } else if (currentState == UIState.GAME && gamePanel != null) {
                                          System.out.println("ClientLog: Attempting to set messageLabel in SERVER_MESSAGE handler (in-game): " + msg);
                                         messageLabel.setText(msg); // Update game panel message label
                                     } else {
                                          System.out.println("ClientLog: Ignoring SERVER_MESSAGE display due to unknown state or null panel.");
                                     }
                                 } else {
                                      System.out.println("ClientLog: Ignoring SERVER_MESSAGE as game is over.");
                                 }
                             });
                        }
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Connection lost. Please restart the client.")
                    );
                    e.printStackTrace();
                    System.exit(0);
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to connect to the server. Please try again.");
            e.printStackTrace();
            System.exit(0);
        }
    }

     private void showGameOver(String winnerInfo) {
         System.out.println("ClientLog: showGameOver called with info: " + winnerInfo);
         isGameOver = true; // Set the flag that the game is over

         // Ensure message label is on the current panel before setting text.
         // It should be on gamePanel or potentially another panel if state change happened rapidly.
         // Let's ensure it's on the game panel if possible, or the waiting panel if we are switching back.
         // For now, showGameOver is expected to be called when gamePanel is active.
         // The messageLabel's parent is handled in switchToGame/switchToWaitingRoom.

         messageLabel.setFont(new Font("Arial", Font.BOLD, 20));
         System.out.println("ClientLog: Attempting to set messageLabel in showGameOver: " + "Game Over! " + winnerInfo);
        messageLabel.setText("Game Over! " + winnerInfo);

         System.out.println("ClientLog: messageLabel should now display: Game Over! " + winnerInfo);


         if (inputField != null) inputField.setEnabled(false);
         if (submitButton != null) submitButton.setEnabled(false);

         // Optional: Add a button or logic to return to the waiting room or exit
         // For now, the leaveGame button exists.
         // Consider switching back to a different panel after a delay or user action
         /*
         new javax.swing.Timer(15000, e -> {
             // Only switch back if still in game over state on this client and current state is GAME
             // Adding state check to prevent switching if player manually left or game ended differently
             if(isGameOver && currentState == UIState.GAME) {
                  System.out.println("ClientLog: Auto-switching from game over to waiting room.");
                  SwingUtilities.invokeLater(this::switchToWaitingRoom);
             }
         }).start();
         */
    }

    private void leaveGame() {
        if (out != null) {
            out.println("LEAVE");
        }
        // --- START OF MODIFIED CODE ---
        // Close streams and socket explicitly before exiting
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            // Assuming socket is managed by the thread that created it,
            // but explicitly closing here is safer if this method is called directly.
            // If connectToServer creates the socket, it might need to be a field.
            // Let's add a socket field.
            // If socket is already closed by server disconnect, this is safe.
             // Re-factoring: Add socket as a field and close it here.
             // This requires changing connectToServer.
             // For now, let's just close streams.
        } catch (IOException e) {
            e.printStackTrace();
        }
        // --- END OF MODIFIED CODE ---
        dispose();
        System.exit(0);
    }

     // --- START OF NEW CODE ---
     // Add socket as a field to manage its closure
     private Socket clientSocket;

     private void connectToServerWithSocketField() {
         try {
             clientSocket = new Socket("localhost", 12345);
             out = new PrintWriter(clientSocket.getOutputStream(), true); // Auto-flush
             in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

             new Thread(() -> {
                 try {
                     String serverMessage;
                     while ((serverMessage = in.readLine()) != null) {
                         System.out.println("ClientLog: Received from server: " + serverMessage); // For debugging

                         if (serverMessage.equals("ENTER_NAME")) {
                             String playerName = JOptionPane.showInputDialog(this, "Enter your name:");
                             if (playerName == null || playerName.trim().isEmpty()) {
                                 playerName = "Player" + (int)(Math.random() * 1000);
                             }
                             out.println(playerName);
                         } else if (serverMessage.startsWith("CONNECTED:")) {
                             updateConnectedList(serverMessage.substring(10));
                         } else if (serverMessage.startsWith("WAITING:")) {
                             updateWaitingList(serverMessage.substring(8));
                         } else if (serverMessage.startsWith("TIMER:")) {
                              if (currentState == UIState.WAITING) {
                                  String timeRemaining = serverMessage.substring(6);
                                  SwingUtilities.invokeLater(() ->
                                      timerLabel.setText("Game starts in: " + timeRemaining + " seconds")
                                  );
                              } else {
                                 System.out.println("ClientLog: Ignoring TIMER message, not in WAITING state. Current state: " + currentState);
                              }
                         } else if (serverMessage.equals("GAME_STARTED")) {
                             SwingUtilities.invokeLater(this::switchToGame);
                         } else if (serverMessage.startsWith("SCORES:")) {
                             updateGamePlayersList(serverMessage.substring(7));
                         } else if (serverMessage.startsWith("PLAYER_LEFT:")) {
                             String playerName = serverMessage.substring(12);
                             SwingUtilities.invokeLater(() -> {
                                 if (!isGameOver) {
                                     String message = playerName + " has left the game.";
                                     if (currentState == UIState.CONNECTED || currentState == UIState.WAITING) {
                                          System.out.println("ClientLog: Attempting to set messageLabel in PLAYER_LEFT handler (pre-game): " + message);
                                          messageLabel.setText(message);
                                          new javax.swing.Timer(7000, e -> {
                                               if (!isGameOver && (currentState == UIState.CONNECTED || currentState == UIState.WAITING)) {
                                                    System.out.println("ClientLog: Attempting to clear messageLabel in PLAYER_LEFT timer (pre-game): \"\"");
                                                   messageLabel.setText("");
                                               } else {
                                                  System.out.println("ClientLog: PLAYER_LEFT timer skipped clearing messageLabel because game is over or state changed.");
                                               }
                                          }).start();
                                     } else if (currentState == UIState.GAME && gamePanel != null) {
                                          System.out.println("ClientLog: Attempting to set messageLabel in PLAYER_LEFT handler (in-game): " + message);
                                          messageLabel.setText(message);
                                     } else {
                                          System.out.println("ClientLog: Ignoring PLAYER_LEFT message display due to unknown state or null panel.");
                                     }
                                 } else {
                                     System.out.println("ClientLog: Ignoring PLAYER_LEFT message as game is over.");
                                 }
                             });
                         }
                          else if (serverMessage.startsWith("GAME_ENDED:")) {
                             String winnerInfo = serverMessage.substring(11);
                              System.out.println("ClientLog: GAME_ENDED received. Winner info: " + winnerInfo);
                             SwingUtilities.invokeLater(() -> showGameOver(winnerInfo));
                             if (questionTimer != null) {
                                  questionTimer.stop();
                              }
                              if (inputField != null) inputField.setEnabled(false);
                              if (submitButton != null) submitButton.setEnabled(false);
                         }
                         else if (serverMessage.startsWith("SERVER_MESSAGE:")) {
                              String msg = serverMessage.substring(15);
                              SwingUtilities.invokeLater(() -> {
                                  if (!isGameOver) {
                                      if (currentState == UIState.CONNECTED || currentState == UIState.WAITING) {
                                           System.out.println("ClientLog: Attempting to set messageLabel in SERVER_MESSAGE handler (pre-game): " + msg);
                                          messageLabel.setText(msg);
                                      } else if (currentState == UIState.GAME && gamePanel != null) {
                                           System.out.println("ClientLog: Attempting to set messageLabel in SERVER_MESSAGE handler (in-game): " + msg);
                                          messageLabel.setText(msg);
                                      } else {
                                           System.out.println("ClientLog: Ignoring SERVER_MESSAGE display due to unknown state or null panel.");
                                      }
                                  } else {
                                       System.out.println("ClientLog: Ignoring SERVER_MESSAGE as game is over.");
                                  }
                              });
                         }
                     }
                 } catch (IOException e) {
                     System.out.println("ClientLog: Connection error: " + e.getMessage());
                     SwingUtilities.invokeLater(() ->
                         JOptionPane.showMessageDialog(this, "Connection lost. Please restart the client.")
                     );
                     e.printStackTrace();
                     System.exit(0);
                 } finally {
                     // Ensure socket is closed if loop exits due to IOException or null readLine
                     try {
                         if (clientSocket != null && !clientSocket.isClosed()) {
                             clientSocket.close();
                             System.out.println("ClientLog: Socket closed in listener thread.");
                         }
                     } catch (IOException e) {
                         e.printStackTrace();
                     }
                 }
             }).start();
         } catch (IOException e) {
             System.out.println("ClientLog: Initial connection failed: " + e.getMessage());
             JOptionPane.showMessageDialog(this, "Unable to connect to the server. Please try again.");
             e.printStackTrace();
             System.exit(0);
         }
     }
     // --- END OF NEW CODE ---


    private void updateConnectedList(String players) {
        SwingUtilities.invokeLater(() -> {
            connectedListModel.clear();
            allConnectedPlayers.clear();
            if (!players.isEmpty()) {
                 for (String player : players.split(",")) {
                    if (!player.isEmpty()) {
                        allConnectedPlayers.add(player);
                        connectedListModel.addElement(player);
                    }
                }
            }
        });
    }

    private void updateWaitingList(String players) {
        SwingUtilities.invokeLater(() -> {
            waitingListModel.clear();
             if (!players.isEmpty()) {
                String[] playerArray = players.split(",");
                for (String player : playerArray) {
                    if (!player.isEmpty()) {
                        waitingListModel.addElement(player);
                    }
                }
            }
        });
    }

    private void updateGamePlayersList(String playerScores) {
        SwingUtilities.invokeLater(() -> {
             // Only update the scoreboard if the game is NOT over and client is in GAME state.
             if (isGameOver || currentState != UIState.GAME) {
                 if(isGameOver) System.out.println("ClientLog: Ignoring SCORES message as game is over.");
                 if(currentState != UIState.GAME) System.out.println("ClientLog: Ignoring SCORES message, not in GAME state. Current state: " + currentState);
                 return;
             }

            gamePlayersListModel.clear();
            if (playerScores.isEmpty()) {
                 System.out.println("ClientLog: updateGamePlayersList received empty scores.");
                return;
            }

            System.out.println("ClientLog: updateGamePlayersList received scores: " + playerScores);

            String[] players = playerScores.split(",");
            for (String playerInfo : players) {
                if (!playerInfo.isEmpty()) {
                    String[] parts = playerInfo.split(":");
                    if (parts.length == 2) {
                        try {
                            String formatted = String.format("%s: %d points",
                                    parts[0],
                                    Integer.parseInt(parts[1]));
                            gamePlayersListModel.addElement(formatted);
                        } catch (NumberFormatException e) {
                            System.err.println("ClientLog: Error parsing score: " + playerInfo);
                        }
                    }
                }
            }
        });
    }

    private void switchToWaitingRoom() {
         System.out.println("ClientLog: Switching to Waiting Room.");
         // Remove currently active panel
         if (currentState == UIState.CONNECTED && connectedPanel.getParent() != null) remove(connectedPanel);
         else if (currentState == UIState.GAME && gamePanel != null) remove(gamePanel);

        // Check if waitingPanel is already parented to avoid errors
         if (waitingPanel.getParent() != null) {
             waitingPanel.getParent().remove(waitingPanel);
         }
        add(waitingPanel, BorderLayout.CENTER);
        revalidate();
        repaint();

         // Ensure message label is visible on the new panel
         // Ensure it's parented to waitingPanel
         if (messageLabel.getParent() != waitingPanel) {
             // Remove from old parent if any
             if (messageLabel.getParent() != null) messageLabel.getParent().remove(messageLabel);
             waitingPanel.add(messageLabel, BorderLayout.SOUTH); // Re-add message label to the waiting panel
         }
          System.out.println("ClientLog: Attempting to set messageLabel in switchToWaitingRoom (clearing): \"\"");
         messageLabel.setText(""); // Clear message on panel switch
         messageLabel.setFont(new Font("Arial", Font.PLAIN, 16)); // Reset font if changed

         currentState = UIState.WAITING; // Update state
         isGameOver = false; // Reset game over flag when switching to waiting
         // Ensure timer label is visible (it's part of waitingPanel)
         timerLabel.setVisible(true);
         // Ensure leave waiting button is visible
         // Find the leave button within waitingPanel and make it visible if necessary
         for(Component comp : waitingPanel.getComponents()) {
             if (comp instanceof JButton && ((JButton)comp).getText().equals("Leave Waiting Room")) {
                 comp.setVisible(true);
                 break;
             }
         }
    }

    private void switchToGame() {
         System.out.println("ClientLog: Switching to Game.");
        // Remove potential old panels
        if (waitingPanel.getParent() != null) remove(waitingPanel);
        if (connectedPanel.getParent() != null) remove(connectedPanel);

        gamePanel = new JPanel(new BorderLayout());

        // --- Scoreboard Panel (Right Side) ---
        JPanel scoreboardPanel = new JPanel(new BorderLayout());
        scoreboardPanel.setBorder(BorderFactory.createTitledBorder("Live Scoreboard"));
        scoreboardPanel.setPreferredSize(new Dimension(250, getHeight()));

        gamePlayersListModel = new DefaultListModel<>();
        gamePlayersList = new JList<>(gamePlayersListModel);
        gamePlayersList.setFont(new Font("Arial", Font.BOLD, 16));

        JScrollPane scoreScrollPane = new JScrollPane(gamePlayersList);
        scoreboardPanel.add(scoreScrollPane, BorderLayout.CENTER);

        JLabel scoreTitle = new JLabel("Current Scores", SwingConstants.CENTER);
        scoreTitle.setFont(new Font("Arial", Font.BOLD, 18));
        scoreboardPanel.add(scoreTitle, BorderLayout.NORTH);

        gamePanel.add(scoreboardPanel, BorderLayout.EAST);

        // --- Game Content Panel (Center) ---
        JPanel gameContent = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        currentStage = 0;
        binaryLabel = new JLabel("Convert to Decimal: " + binaryStages[currentStage], SwingConstants.CENTER);
        binaryLabel.setFont(new Font("Arial", Font.BOLD, 36));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gameContent.add(binaryLabel, gbc);

        questionTimerLabel = new JLabel("Time left: 20 sec", SwingConstants.CENTER);
        questionTimerLabel.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridy = 1;
        gameContent.add(questionTimerLabel, gbc);

        inputField = new JTextField(15);
        inputField.setFont(new Font("Arial", Font.PLAIN, 24));
        inputField.setEnabled(true);
        gbc.gridy = 2;
        gameContent.add(inputField, gbc);

        submitButton = new JButton("Submit Answer");
        submitButton.setFont(new Font("Arial", Font.BOLD, 20));
        submitButton.addActionListener(e -> checkAnswer());
        submitButton.setEnabled(true);
        gbc.gridy = 3;
        gameContent.add(submitButton, gbc);

        // Message Label (for feedback like Correct/Incorrect, Time's up, Game Over)
        // Ensure messageLabel is added to the game content panel
         if (messageLabel.getParent() != null) {
             messageLabel.getParent().remove(messageLabel); // Remove from previous parent if any
         }
        gbc.gridy = 4;
        gameContent.add(messageLabel, gbc);

         System.out.println("ClientLog: Attempting to set messageLabel in switchToGame (clearing): \"\"");
        messageLabel.setText(""); // Clear any previous messages
        messageLabel.setFont(new Font("Arial", Font.ITALIC, 18)); // Set font for in-game messages

        // Leave Game Button
        JButton leaveGameButton = new JButton("Leave Game");
        leaveGameButton.setFont(new Font("Arial", Font.PLAIN, 16));
        leaveGameButton.addActionListener(e -> leaveGame());
        gbc.gridy = 5;
        gameContent.add(leaveGameButton, gbc);

        gamePanel.add(gameContent, BorderLayout.CENTER);

        add(gamePanel, BorderLayout.CENTER);
        revalidate();
        repaint();

        score = 0;
        currentState = UIState.GAME; // Update state
        isGameOver = false; // Reset game over flag for the new game
        // Ensure timer label is hidden or removed from waitingPanel
        // timerLabel is part of waitingPanel, so removing waitingPanel hides it.
        // Ensure play/leave buttons from connected panel are not visible
        playButton.setVisible(false);
        leaveButton.setVisible(false);


        startQuestionTimer();
    }

    private void startQuestionTimer() {
        questionTimeLeft = 20;
        questionTimerLabel.setText("Time left: " + questionTimeLeft + " sec");
        if (questionTimer != null) {
            questionTimer.stop();
        }
        questionTimer = new javax.swing.Timer(1000, e -> {
             if (isGameOver) {
                 if (questionTimer != null) questionTimer.stop();
                 System.out.println("ClientLog: Question timer stopped because game is over.");
                 return;
             }

            questionTimeLeft--;
            questionTimerLabel.setText("Time left: " + questionTimeLeft + " sec");

            if (questionTimeLeft <= 0) {
                questionTimer.stop();
                 // Only set message if game is not over
                 if (!isGameOver) {
                      System.out.println("ClientLog: Attempting to set messageLabel in startQuestionTimer (Time's up): \"Time's up!\"");
                     messageLabel.setText("Time's up!");
                 }


                 if (currentStage == binaryStages.length - 1) {
                     System.out.println("ClientLog: Time's up on final question. Sending GAME_FINISHED.");
                     out.println("GAME_FINISHED");
                      inputField.setEnabled(false);
                      submitButton.setEnabled(false);
                 } else {
                    moveToNextQuestion();
                 }
            }
        });
        questionTimer.start();
    }

    private void moveToNextQuestion() {
        currentStage++;
         System.out.println("ClientLog: Moving to stage: " + currentStage);

        if (currentStage < binaryStages.length) {
            binaryLabel.setText("Convert to Decimal: " + binaryStages[currentStage]);
            inputField.setText("");
             // Only clear message label if game is not over
             if (!isGameOver) {
                  System.out.println("ClientLog: Attempting to set messageLabel in moveToNextQuestion (clearing): \"\"");
                 messageLabel.setText("");
             } else {
                 System.out.println("ClientLog: moveToNextQuestion skipped clearing messageLabel because game is over.");
             }
            startQuestionTimer();
        } else {
             System.out.println("ClientLog: Player completed all questions locally.");
             inputField.setEnabled(false);
             submitButton.setEnabled(false);
        }
    }

    private void checkAnswer() {
         if (isGameOver) {
             System.out.println("ClientLog: Ignoring answer input as game is over.");
             return;
         }

        String input = inputField.getText().trim();
        if (input.isEmpty()) {
              // Only set message if game is not over
              if (!isGameOver) {
                   System.out.println("ClientLog: Attempting to set messageLabel in checkAnswer (Empty input): \"Please enter an answer.\"");
                  messageLabel.setText("Please enter an answer.");
              }
             return;
        }

        try {
            int decimalValue = Integer.parseInt(input);
            boolean correct = false;
            int correctAnswer = Integer.parseInt(binaryStages[currentStage], 2);

             System.out.println("ClientLog: Checking answer for stage " + currentStage + ". Input: " + input + ", Correct: " + correctAnswer);


            if (decimalValue == correctAnswer) {
                correct = true;
            }

            if (correct) {
                if (questionTimer != null) {
                    questionTimer.stop();
                }
                score += 10;
                 System.out.println("ClientLog: Correct answer. New score: " + score + ". Sending UPDATE_SCORE:" + score);
                out.println("UPDATE_SCORE:" + score);

                 currentStage++;

                 if (currentStage < binaryStages.length) {
                     binaryLabel.setText("Convert to Decimal: " + binaryStages[currentStage]);
                     inputField.setText("");
                      // Only set message if game is not over
                     if (!isGameOver) {
                          System.out.println("ClientLog: Attempting to set messageLabel in checkAnswer (Correct): \"Correct! Moving to the next stage...\"");
                         messageLabel.setText("Correct! Moving to the next stage...");
                     }
                     startQuestionTimer();
                 } else {
                      System.out.println("ClientLog: Attempting to set messageLabel in checkAnswer (Correct, Completed): \"Correct! You have completed all questions!\"");
                     messageLabel.setText("Correct! You have completed all questions!"); // This message will be replaced by GAME_ENDED from server
                     inputField.setEnabled(false);
                     submitButton.setEnabled(false);
                      System.out.println("ClientLog: Correct answer on final question. Sending GAME_FINISHED.");
                     out.println("GAME_FINISHED");
                 }

            } else {
                 System.out.println("ClientLog: Incorrect answer.");
                 // Only set message if game is not over
                 if (!isGameOver) {
                      System.out.println("ClientLog: Attempting to set messageLabel in checkAnswer (Incorrect): \"Incorrect! Try again.\"");
                     messageLabel.setText("Incorrect! Try again.");
                 }
            }
        } catch (NumberFormatException e) {
             System.out.println("ClientLog: Invalid input format: " + input);
             // Only set message if game is not over
              if (!isGameOver) {
                   System.out.println("ClientLog: Attempting to set messageLabel in checkAnswer (Invalid format): \"Please enter a valid decimal number.\"");
                  messageLabel.setText("Please enter a valid decimal number.");
              }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}
