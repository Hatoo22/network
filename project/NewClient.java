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
            // Ask for the player's name
            out.println("Enter your name:");
            playerName = in.readLine();

            synchronized (playerNames) {
                playerNames.add(playerName);
            }

            System.out.println(playerName + " has joined the game.");
            sendPlayerList();

            while (true) {
                String request = in.readLine();
                if (request == null) break;
                broadcastMessage(playerName + ": " + request);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            synchronized (playerNames) {
                playerNames.remove(playerName);
            }
            clients.remove(this);
            sendPlayerList();
            closeResources();
        }
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

    private void sendPlayerList() {
        String playerList = "Players in the game: " + String.join(", ", playerNames);
        for (NewClient client : clients) {
            client.sendMessage(playerList);
        }
    }

    private void closeResources() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (client != null) client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
