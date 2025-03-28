/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package network.project;



import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

class NewClient implements Runnable {
    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private ArrayList<NewClient> clients;
    private static ArrayList<String> playerNames = new ArrayList<>();
    private String playerName;

    public NewClient(Socket socket, ArrayList<NewClient> clients) throws IOException {
        this.client = socket;
        this.clients = clients;
        in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        out = new PrintWriter(client.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            out.println("Enter your name:");
            playerName = in.readLine();
            if (playerName == null || playerName.trim().isEmpty()) {
                playerName = "Unknown";
            }
            synchronized (playerNames) {
                playerNames.add(playerName);
            }
            System.out.println(playerName + " has joined the game.");
            sendPlayerList();

            String request;
            while ((request = in.readLine()) != null) {
                System.out.println("Received from " + playerName + ": " + request);
                if (request.equalsIgnoreCase("LEAVE")) {
                    break;
                }
                broadcastMessage(playerName + ": " + request);
            }
        } catch (IOException e) {
            System.out.println("Connection lost with " + playerName);
        } finally {
            handleDisconnect();
        }
    }

    private void handleDisconnect() {
        synchronized (playerNames) {
            if (playerName != null) {
                playerNames.remove(playerName);
            }
        }
        clients.remove(this);
        sendPlayerList();
        broadcastMessage(playerName + " has left the game.");
        closeResources();
        System.out.println("Cleanup complete for " + playerName);
    }

    private void broadcastMessage(String message) {
        for (NewClient client : clients) {
            client.sendMessage(message);
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // Here, we use the prefix "CONNECTED:" so that clients update their GUI accordingly.
    private void sendPlayerList() {
        String playerList = "CONNECTED:" + String.join(",", playerNames);
        for (NewClient client : clients) {
            client.sendMessage(playerList);
        }
    }

    private void closeResources() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (client != null && !client.isClosed()) client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
