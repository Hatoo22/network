package network.project;

import java.io.*;
import java.net.*;
import java.util.*;

public class NewServer {
    private static final int PORT = 12345;
    private static List<ClientHandler> connectedPlayers = new ArrayList<>();
    private static List<String> waitingRoom = new ArrayList<>();
    private static List<String> gamePlayers = new ArrayList<>();

    private static boolean isTimerRunning = false;
    private static Timer timer;
    private static int countdownSeconds = 30;

    // خريطة لتخزين نقاط كل لاعب
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

    // تحديث جميع العملاء بقوائم اللاعبين المتصلين والمنتظرين.
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

    // الحصول على قائمة الأسماء المتصلة كقائمة مفصولة بفواصل.
    private static String getConnectedPlayerNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : connectedPlayers) {
            names.add(client.getPlayerName());
        }
        return String.join(",", names);
    }

    // بدء اللعبة لجميع اللاعبين في غرفة الانتظار.
    private static synchronized void startGame() {
    // Move waiting players to game players
    gamePlayers = new ArrayList<>(waitingRoom);
    waitingRoom.clear();
    
    // Initialize scores for game players
    synchronized (scoreboard) {
        for (String player : gamePlayers) {
            scoreboard.put(player, 0);
        }
    }
    
    String startMessage = "GAME_STARTED";
    synchronized (connectedPlayers) {
        for (ClientHandler client : connectedPlayers) {
            if (gamePlayers.contains(client.getPlayerName())) {
                client.sendMessage(startMessage);
            }
        }
    }
    broadcastScores();
    updateAllClients();
}
    // فحص ما إذا كان يمكن بدء اللعبة.
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

    // بدء عد تنازلي.
    private static void startCountdownTimer() {
        if (isTimerRunning) return;
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

    // إرسال النقاط المحدثة لجميع العملاء.
private static synchronized void broadcastScores() {
    StringBuilder sb = new StringBuilder("SCORES:");
    synchronized (gamePlayers) { // Ensure thread-safe iteration
        synchronized (scoreboard) {
            for (String player : gamePlayers) {
                sb.append(player).append(":").append(scoreboard.get(player)).append(",");
            }
        }
    }
    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
        sb.deleteCharAt(sb.length() - 1);
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

                // طلب اسم اللاعب
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
                    } else if (input.startsWith("UPDATE_SCORE:")) {
                        try {
                            int newScore = Integer.parseInt(input.substring(13).trim());
                            synchronized (scoreboard) {
                                scoreboard.put(playerName, newScore);
                            }
                            broadcastScores();
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid score format from " + playerName);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection lost with " + playerName);
            } finally {
    synchronized (connectedPlayers) {
        connectedPlayers.remove(this);
    }
    synchronized (waitingRoom) {
        waitingRoom.remove(playerName);
    }
    
    boolean wasInGame = false;
    synchronized (gamePlayers) {
        wasInGame = gamePlayers.remove(playerName);
    }
    synchronized (scoreboard) {
        scoreboard.remove(playerName);
    }
    
    if (wasInGame) {
        String leaveMessage = "PLAYER_LEFT:" + playerName;
        synchronized (connectedPlayers) {
            for (ClientHandler client : connectedPlayers) {
                client.sendMessage(leaveMessage);
            }
        }
    }
    
    updateAllClients();
    broadcastScores();
    
    try {
        socket.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
}
        }

        // إرسال رسالة إلى هذا العميل.
        public void sendMessage(String message) {
            out.println(message);
        }
    }
}
