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
    private JPanel gamePanel;
    private JLabel binaryLabel;
    private JTextField inputField;
    private JButton submitButton;
    private JLabel messageLabel;
    private JLabel timerLabel;
    private JLabel scoreLabel;
    private HashSet<String> allConnectedPlayers = new HashSet<>();
    private PrintWriter out;
    private BufferedReader in;
    private int currentStage = 1;
    private int score = 0;
    private javax.swing.Timer questionTimer;
    private int questionTimeLeft;
    private JLabel questionTimerLabel;

    private String[] binaryStages = {"1010", "0011", "11000", "100110", "110110"};

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
        connectedPanel.add(messageLabel, BorderLayout.NORTH);

        // Button panel for connected screen
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        playButton = new JButton("Play");
        playButton.setFont(new Font("Arial", Font.BOLD, 20));
        playButton.addActionListener((ActionEvent e) -> {
            out.println("READY");
            switchToWaitingRoom();
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

        timerLabel = new JLabel("Timer: Not started", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 24));
        waitingPanel.add(timerLabel, BorderLayout.NORTH);

        // Add leave button to waiting panel
        JButton leaveWaitingButton = new JButton("Leave Waiting Room");
        leaveWaitingButton.setFont(new Font("Arial", Font.BOLD, 20));
        leaveWaitingButton.addActionListener(e -> leaveGame());
        waitingPanel.add(leaveWaitingButton, BorderLayout.SOUTH);

        add(connectedPanel, BorderLayout.CENTER);
        connectToServer();

        setVisible(true);
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
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
                            String timeRemaining = serverMessage.substring(6);
                            SwingUtilities.invokeLater(() ->
                                timerLabel.setText("Timer: " + timeRemaining + " seconds remaining")
                            );
                        } else if (serverMessage.equals("GAME_STARTED")) {
                            SwingUtilities.invokeLater(this::switchToGame);
                        } else if (serverMessage.startsWith("SCORES:")) {
                            updateGamePlayersList(serverMessage.substring(7));
                            
                        }
                        else if (serverMessage.startsWith("PLAYER_LEFT:")) {
    String playerName = serverMessage.substring(12);
    SwingUtilities.invokeLater(() -> {
        messageLabel.setText(playerName + " has left the game.");
        new javax.swing.Timer(7000, e -> messageLabel.setText("")).start();
    });
}
                    }
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Connection lost. Please restart the client.");
                    System.exit(0);
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to connect to the server. Please try again.");
            System.exit(0);
        }
    }

    private void leaveGame() {
        if (out != null) {
            out.println("LEAVE");
        }
        dispose();
        System.exit(0);
    }

    private void updateConnectedList(String players) {
        SwingUtilities.invokeLater(() -> {
            connectedListModel.clear();
            allConnectedPlayers.clear();
            for (String player : players.split(",")) {
                if (!player.isEmpty()) {
                    allConnectedPlayers.add(player);
                }
            }
            for (String player : allConnectedPlayers) {
                connectedListModel.addElement(player);
            }
        });
    }

    private void updateWaitingList(String players) {
        SwingUtilities.invokeLater(() -> {
            waitingListModel.clear();
            String[] playerArray = players.split(",");
            for (String player : playerArray) {
                if (!player.isEmpty() && !waitingListModel.contains(player)) {
                    waitingListModel.addElement(player);
                }
            }
        });
    }

   private void updateGamePlayersList(String playerScores) {
     SwingUtilities.invokeLater(() -> {
        gamePlayersListModel.clear();
        if (playerScores.isEmpty()) return;
        
        String[] players = playerScores.split(",");
        for (String playerInfo : players) {
            if (!playerInfo.isEmpty()) {
                String[] parts = playerInfo.split(":");
                if (parts.length == 2) {
                    String formatted = String.format("%s: %d points", 
                        parts[0], 
                        Integer.parseInt(parts[1]));
                    gamePlayersListModel.addElement(formatted);
                }
            }
        }
    });
}

    private void switchToWaitingRoom() {
        remove(connectedPanel);
        add(waitingPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void switchToGame() {
    remove(waitingPanel);
    gamePanel = new JPanel(new BorderLayout());

    // Scoreboard Panel (Right Side)
    JPanel scoreboardPanel = new JPanel(new BorderLayout());
    scoreboardPanel.setBorder(BorderFactory.createTitledBorder("Live Scoreboard"));
    scoreboardPanel.setPreferredSize(new Dimension(250, getHeight()));
    
    gamePlayersListModel = new DefaultListModel<>();
    gamePlayersList = new JList<>(gamePlayersListModel);
    gamePlayersList.setFont(new Font("Arial", Font.BOLD, 16));
    
    JScrollPane scoreScrollPane = new JScrollPane(gamePlayersList);
    scoreboardPanel.add(scoreScrollPane, BorderLayout.CENTER);
    
    // Add title to scoreboard
    JLabel scoreTitle = new JLabel("Current Scores", SwingConstants.CENTER);
    scoreTitle.setFont(new Font("Arial", Font.BOLD, 18));
    scoreboardPanel.add(scoreTitle, BorderLayout.NORTH);
    
    gamePanel.add(scoreboardPanel, BorderLayout.EAST);

    // Game Content Panel (Center)
    JPanel gameContent = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(15, 15, 15, 15);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Question Display
    binaryLabel = new JLabel("Convert to Decimal: " + binaryStages[0], SwingConstants.CENTER);
    binaryLabel.setFont(new Font("Arial", Font.BOLD, 36));
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gameContent.add(binaryLabel, gbc);

    // Question Timer
    questionTimerLabel = new JLabel("Time left: 20 sec", SwingConstants.CENTER);
    questionTimerLabel.setFont(new Font("Arial", Font.BOLD, 24));
    gbc.gridy = 1;
    gameContent.add(questionTimerLabel, gbc);

    // Answer Input
    inputField = new JTextField(15);
    inputField.setFont(new Font("Arial", Font.PLAIN, 24));
    gbc.gridy = 2;
    gameContent.add(inputField, gbc);

    // Submit Button
    submitButton = new JButton("Submit Answer");
    submitButton.setFont(new Font("Arial", Font.BOLD, 20));
    submitButton.addActionListener(e -> checkAnswer());
    gbc.gridy = 3;
    gameContent.add(submitButton, gbc);

    // Message Label
    messageLabel = new JLabel("", SwingConstants.CENTER);
    messageLabel.setFont(new Font("Arial", Font.ITALIC, 18));
    gbc.gridy = 4;
    gameContent.add(messageLabel, gbc);

    // Leave Game Button
    JButton leaveGameButton = new JButton("Leave Game");
    leaveGameButton.setFont(new Font("Arial", Font.PLAIN, 16));
    leaveGameButton.addActionListener(e -> leaveGame());
    gbc.gridy = 5;
    gameContent.add(leaveGameButton, gbc);

    gamePanel.add(gameContent, BorderLayout.CENTER);

    // Final Setup
    add(gamePanel, BorderLayout.CENTER);
    revalidate();
    repaint();
    
    // Initialize game state
    currentStage = 0; // Start with first question index
    score = 0;
    startQuestionTimer();
}
    private void startQuestionTimer() {
        questionTimeLeft = 20;
        questionTimerLabel.setText("Time left: " + questionTimeLeft + " sec");
        if (questionTimer != null) {
            questionTimer.stop();
        }
        questionTimer = new javax.swing.Timer(1000, e -> {
            questionTimeLeft--;
            questionTimerLabel.setText("Time left: " + questionTimeLeft + " sec");
            if (questionTimeLeft <= 0) {
                questionTimer.stop();
                messageLabel.setText("Time's up! Moving to next question...");
                moveToNextQuestion();
            }
        });
        questionTimer.start();
    }

    private void moveToNextQuestion() {
        currentStage++;
        if (currentStage < binaryStages.length) {
            binaryLabel.setText("Binary number: " + binaryStages[currentStage]);
            inputField.setText("");
            startQuestionTimer();
        } else {
            messageLabel.setText("Game Over!");
            submitButton.setEnabled(false);
        }
    }

 private void checkAnswer() {
    String input = inputField.getText();
    try {
        int decimalValue = Integer.parseInt(input);
        boolean correct = false;

        // Check the answer based on the current stage with correct indices
        if (currentStage == 0 && decimalValue == 10) {
            correct = true;
        } else if (currentStage == 1 && decimalValue == 3) {
            correct = true;
        } else if (currentStage == 2 && decimalValue == 24) {
            correct = true;
        } else if (currentStage == 3 && decimalValue == 38) {
            correct = true;
        } else if (currentStage == 4 && decimalValue == 54) {
            correct = true;
        }

        if (correct) {
            if (questionTimer != null) {
                questionTimer.stop();
            }
            score += 10;
            out.println("UPDATE_SCORE:" + score);

            currentStage++; // Move to the next stage first

            if (currentStage < binaryStages.length) {
                binaryLabel.setText("Convert to Decimal: " + binaryStages[currentStage]);
                inputField.setText("");
                messageLabel.setText("Correct! Moving to the next stage...");
                startQuestionTimer();
            } else {
                messageLabel.setText("Congratulations! You won the game!");
                submitButton.setEnabled(false);
            }
        } else {
            messageLabel.setText("Incorrect! Try again.");
        }
    } catch (NumberFormatException e) {
        messageLabel.setText("Please enter a valid decimal number.");
    }
}

    public static void main(String[] args) {
        new Client();
    }
}
