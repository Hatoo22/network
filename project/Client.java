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
    private DefaultListModel<String> gamePlayersListModel; // قائمة اللاعبين في اللعبة
    private JList<String> connectedList;
    private JList<String> waitingList;
    private JList<String> gamePlayersList;
    private JButton playButton;
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

    public Client() {
        setTitle("Game Client");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Create a message label for notifications
        messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        connectedPanel = new JPanel(new BorderLayout());
        connectedPanel.setBorder(BorderFactory.createTitledBorder("Connected Players"));
        connectedListModel = new DefaultListModel<>();
        connectedList = new JList<>(connectedListModel);
        connectedList.setFont(new Font("Arial", Font.PLAIN, 18));
        connectedPanel.add(new JScrollPane(connectedList), BorderLayout.CENTER);

        // Add the messageLabel to the connected panel
        connectedPanel.add(messageLabel, BorderLayout.NORTH);

        playButton = new JButton("Play");
        playButton.setFont(new Font("Arial", Font.BOLD, 20));
        playButton.addActionListener((ActionEvent e) -> {
            out.println("READY");
            switchToWaitingRoom();
        });
        connectedPanel.add(playButton, BorderLayout.SOUTH);

        waitingPanel = new JPanel(new BorderLayout());
        waitingPanel.setBorder(BorderFactory.createTitledBorder("Waiting Room"));
        waitingListModel = new DefaultListModel<>();
        waitingList = new JList<>(waitingListModel);
        waitingList.setFont(new Font("Arial", Font.PLAIN, 18));
        waitingPanel.add(new JScrollPane(waitingList), BorderLayout.CENTER);

        timerLabel = new JLabel("Timer: Not started", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 24));
        waitingPanel.add(timerLabel, BorderLayout.NORTH);

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
                            out.println(playerName);
                        } else if (serverMessage.startsWith("CONNECTED:")) {
                            updateConnectedList(serverMessage.substring(10));
                        } else if (serverMessage.startsWith("WAITING:")) {
                            updateWaitingList(serverMessage.substring(8));
                        } else if (serverMessage.startsWith("TIMER:")) {
                            String timeRemaining = serverMessage.substring(6);SwingUtilities.invokeLater(() -> timerLabel.setText("Timer: " + timeRemaining + " seconds remaining"));
                        } else if (serverMessage.equals("GAME_STARTED")) {
                            SwingUtilities.invokeLater(this::switchToGame);
                        } else if (serverMessage.startsWith("SCORES:")) {
                            updateGamePlayersList(serverMessage.substring(7));
                        }
                    }
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Connection lost. Please restart the client.");
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to connect to the server. Please try again.");
        }
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

    private void switchToWaitingRoom() {
        remove(connectedPanel);
        add(waitingPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void switchToGame() {
        remove(waitingPanel);
        gamePanel = new JPanel(new BorderLayout());

        // Player list on the right
        JPanel playerListPanel = new JPanel(new BorderLayout());
        playerListPanel.setBorder(BorderFactory.createTitledBorder("Players & Scores"));
        gamePlayersListModel = new DefaultListModel<>();
        gamePlayersList = new JList<>(gamePlayersListModel);
        gamePlayersList.setFont(new Font("Arial", Font.PLAIN, 16));
        playerListPanel.add(new JScrollPane(gamePlayersList), BorderLayout.CENTER);
        gamePanel.add(playerListPanel, BorderLayout.EAST);

        // Game content in the center
        JPanel gameContentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;

        binaryLabel = new JLabel("Binary number: 1010", SwingConstants.CENTER);
        binaryLabel.setFont(new Font("Arial", Font.BOLD, 50));
        gameContentPanel.add(binaryLabel, gbc);

        inputField = new JTextField(20);
        inputField.setFont(new Font("Arial", Font.PLAIN, 30));
        gbc.gridy = 1;
        gameContentPanel.add(inputField, gbc);

        submitButton = new JButton("Submit");
        submitButton.setFont(new Font("Arial", Font.PLAIN, 30));
        submitButton.addActionListener((ActionEvent e) -> checkAnswer());
        gbc.gridy = 2;
        gameContentPanel.add(submitButton, gbc);

        messageLabel = new JLabel("");
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 24));
        gbc.gridy = 3;
        gbc.insets = new Insets(20, 0, 0, 0);
        gameContentPanel.add(messageLabel, gbc);

        scoreLabel = new JLabel("Score: 0", SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridy = 4;
        gameContentPanel.add(scoreLabel, gbc);

        gamePanel.add(gameContentPanel, BorderLayout.CENTER);

        add(gamePanel, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    private void updateGamePlayersList(String playerScores) {
        SwingUtilities.invokeLater(() -> {gamePlayersListModel.clear();
            String[] players = playerScores.split(",");
            for (String playerInfo : players) {
                if (!playerInfo.isEmpty()) {
                    gamePlayersListModel.addElement(playerInfo);
                }
            }
        });
    }

    private void checkAnswer() {
        String input = inputField.getText();
        try {
            int decimalValue = Integer.parseInt(input, 10);
            if ((currentStage == 1 && decimalValue == 10) || (currentStage == 2 && decimalValue == 3)) {
                score += 10;
                scoreLabel.setText("Score: " + score);
                out.println("UPDATE_SCORE:" + score); // إرسال النقاط إلى الخادم
                if (currentStage == 1) {
                    messageLabel.setText("Correct! Moving to the next stage...");
                    binaryLabel.setText("Binary number: 0011");
                    inputField.setText("");
                    currentStage++;
                } else if (score >= 20) {
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
