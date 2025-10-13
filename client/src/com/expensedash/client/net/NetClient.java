package com.expensedash.client.net;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class NetClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Consumer<String> messageHandler;

    /**
     * Connects to the given host and port.
     * @param host Server IP (e.g., "127.0.0.1" or LAN IP)
     * @param port Server port (e.g., 5055)
     * @param onMessage Initial message handler (optional, can be null)
     */
    public void connect(String host, int port, Consumer<String> onMessage) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        if (onMessage != null)
            this.messageHandler = onMessage;

        Thread listener = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (messageHandler != null) {
                        messageHandler.accept(line);
                    }
                }
            } catch (IOException e) {
                System.out.println("[NetClient] Disconnected: " + e.getMessage());
            } finally {
                close();
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Allows controllers to change the message handler dynamically
     * (e.g., switch from LoginController to DashboardController).
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /** Sends a line to the server. */
    public void send(String msg) {
        if (out != null) {
            out.println(msg);
        } else {
            System.out.println("[NetClient] Attempted to send, but connection not established.");
        }
    }

    /** Closes the connection gracefully. */
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException ignored) {}
    }
}
