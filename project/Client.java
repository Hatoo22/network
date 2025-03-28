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
    private DefaultListModel<String> gamePlayersListModel; // قائمة اللاعبين في اللعبة (مع النقاط)
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
    private JLabel scoreLabel; // لن يتم عرضه داخل واجهة اللعبة
    private HashSet<String> allConnectedPlayers = new HashSet<>();
    private PrintWriter out;
    private BufferedReader in;
    private int currentStage = 1;
    private int score = 0;
    
    // مؤقت غرفة الانتظار (يتم تحديثه من السيرفر)
    // مؤقت السؤال الخاص باللعبة:
    private javax.swing.Timer questionTimer;
    private int questionTimeLeft;
    private JLabel questionTimerLabel; // عرض مؤقت السؤال داخل لوحة اللعبة

    // تحديث الأسئلة: الآن تحتوي على 5 أسئلة
    // السؤال الأول: "1010" -> الإجابة 10
    // السؤال الثاني: "0011" -> الإجابة 3
    // السؤال الثالث: "11000" -> الإجابة 24
    // السؤال الرابع: "100110" -> الإجابة 38
    // السؤال الخامس: "110110" -> الإجابة 54
    private String[] binaryStages = {"1010", "0011", "11000", "100110", "110110"};

    public Client() {
        setTitle("Game Client");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // رسالة التنبيه
        messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        // لوحة اللاعبين المتصلين
        connectedPanel = new JPanel(new BorderLayout());
        connectedPanel.setBorder(BorderFactory.createTitledBorder("Connected Players"));
        connectedListModel = new DefaultListModel<>();
        connectedList = new JList<>(connectedListModel);
        connectedList.setFont(new Font("Arial", Font.PLAIN, 18));
        connectedPanel.add(new JScrollPane(connectedList), BorderLayout.CENTER);
        connectedPanel.add(messageLabel, BorderLayout.NORTH);

        playButton = new JButton("Play");
        playButton.setFont(new Font("Arial", Font.BOLD, 20));
        playButton.addActionListener((ActionEvent e) -> {
            out.println("READY");
            switchToWaitingRoom();
        });
        connectedPanel.add(playButton, BorderLayout.SOUTH);

        // لوحة الانتظار
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
                            String timeRemaining = serverMessage.substring(6);
                            SwingUtilities.invokeLater(() ->
                                timerLabel.setText("Timer: " + timeRemaining + " seconds remaining")
                            );
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

    private void updateGamePlayersList(String playerScores) {
        SwingUtilities.invokeLater(() -> {
            gamePlayersListModel.clear();
            String[] players = playerScores.split(",");
            for (String playerInfo : players) {
                if (!playerInfo.isEmpty()) {
                    gamePlayersListModel.addElement(playerInfo);
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

        // لوحة قائمة اللاعبين داخل اللعبة على اليمين (Scoreboard)
        JPanel playerListPanel = new JPanel(new BorderLayout());
        playerListPanel.setBorder(BorderFactory.createTitledBorder("Players & Scores"));
        gamePlayersListModel = new DefaultListModel<>();
        gamePlayersList = new JList<>(gamePlayersListModel);
        gamePlayersList.setFont(new Font("Arial", Font.PLAIN, 16));
        playerListPanel.add(new JScrollPane(gamePlayersList), BorderLayout.CENTER);
        gamePanel.add(playerListPanel, BorderLayout.EAST);

        // محتوى اللعبة في الوسط
        JPanel gameContentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // السؤال الأول باستخدام binaryStages[0]
        binaryLabel = new JLabel("Binary number: " + binaryStages[0], SwingConstants.CENTER);
        binaryLabel.setFont(new Font("Arial", Font.BOLD, 50));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gameContentPanel.add(binaryLabel, gbc);

        // مؤقت السؤال (20 ثانية)
        questionTimerLabel = new JLabel("Time left: 20 sec", SwingConstants.CENTER);
        questionTimerLabel.setFont(new Font("Arial", Font.BOLD, 28));
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gameContentPanel.add(questionTimerLabel, gbc);

        inputField = new JTextField(20);
        inputField.setFont(new Font("Arial", Font.PLAIN, 30));
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gameContentPanel.add(inputField, gbc);

        submitButton = new JButton("Submit");
        submitButton.setFont(new Font("Arial", Font.PLAIN, 30));
        submitButton.addActionListener((ActionEvent e) -> checkAnswer());
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gameContentPanel.add(submitButton, gbc);

        messageLabel = new JLabel("");
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 24));
        gbc.gridy = 4;
        gbc.insets = new Insets(20, 0, 0, 0);
        gameContentPanel.add(messageLabel, gbc);

        // تم إزالة عرض scoreLabel من محتوى اللعبة، بحيث تظهر النقاط فقط في قائمة المتصدرين

        gamePanel.add(gameContentPanel, BorderLayout.CENTER);

        add(gamePanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        startQuestionTimer();
    }

    // يبدأ مؤقت السؤال لمدة 20 ثانية دون التأثير على مؤقت غرفة الانتظار
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

    // عند انتهاء وقت السؤال أو بعد إجابة صحيحة، ينتقل للسؤال التالي
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
            int decimalValue = Integer.parseInt(input, 10);
            if ((currentStage == 1 && decimalValue == 10) ||
                (currentStage == 2 && decimalValue == 3) ||
                (currentStage == 3 && decimalValue == 24) ||
                (currentStage == 4 && decimalValue == 38) ||
                (currentStage == 5 && decimalValue == 54)) {
                if (questionTimer != null) {
                    questionTimer.stop();
                }
                score += 10;
                out.println("UPDATE_SCORE:" + score); // إرسال النقاط إلى الخادم
                if (currentStage < binaryStages.length) {
                    messageLabel.setText("Correct! Moving to the next stage...");
                    binaryLabel.setText("Binary number: " + binaryStages[currentStage]); // عرض السؤال التالي
                    inputField.setText("");
                    currentStage++;
                    if (currentStage <= binaryStages.length) {
                        startQuestionTimer();
                    }
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
