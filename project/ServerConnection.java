/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package network.project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 *
 * @author rubas
 */
class ServerConnection implements Runnable {
    private Socket server;
    private BufferedReader in;
    private PrintWriter out;
      
    public ServerConnection(Socket s) throws IOException {
        server = s;
        in = new BufferedReader(new InputStreamReader(server.getInputStream())); // Read from server
        out = new PrintWriter(server.getOutputStream(), true); // Send to server
    }
    
    @Override
    public void run() {
        String serverResponse;
        try {
            while (true) {
                serverResponse = in.readLine(); // Read response from server
                if (serverResponse == null) break; // Exit if server disconnects
                System.out.println("Server says: " + serverResponse);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                in.close(); // Close input stream
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }     
    }           
}
