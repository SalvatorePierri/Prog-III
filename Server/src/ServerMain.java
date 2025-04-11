import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class ServerMain {
    private static final Object fileLock = new Object();
    private static final int PORT = 1234;
    private static final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
    private static final Map<String, Socket> activeClients = new ConcurrentHashMap<>();
    private static final String MESSAGE_FILE = "messages.txt";
    private static String Admin;

    public static void main(String[] args) {
        System.out.println("Mail Server avviato sulla porta " + PORT);

        loadMessages();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }catch (IOException e) {
            System.err.println("Errore nel server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        String clientEmail = null;
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            StringBuilder inputBuilder = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.equals("---END---")) {
                inputBuilder.append(line).append("\n");
            }
            String input = inputBuilder.toString();
            if (input.isEmpty()) {
                return; // Non aggiungere il socket alla coda
            }
            //System.out.println("Messaggio ricevuto: " + input);
            if (input.startsWith("CLIENT:")) {
                clientEmail = input.substring("CLIENT:".length()).trim().toLowerCase();
                if(clientEmail.contains(" role: admin")) {
                    Admin = clientEmail.replace(" role: admin", "").trim();
                    activeClients.put(Admin, socket);
                    System.out.println("Andmin registrato: " + Admin);
                    while (!socket.isClosed() && !socket.isInputShutdown()) {
                        try {
                            // Attende un po' prima di controllare di nuovo
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }else {
                    activeClients.put(clientEmail, socket);
                    System.out.println("Client registrato: " + clientEmail);
                    // Invia al client tutti i messaggi pendenti destinati a lui
                    sendPendingMessages(clientEmail, out);
                    while (!socket.isClosed() && !socket.isInputShutdown()) {
                        try {
                            // Attende un po' prima di controllare di nuovo
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } else {
                saveMessage(input);
                messages.add(input);
                System.out.println("Messaggio ricevuto e salvato: \n" + input);
                String recipient = extractRecipient(input);
                if (recipient != null) {
                    recipient = recipient.toLowerCase();
                    if (activeClients.containsKey(recipient)) {
                        try {
                            Socket recipientSocket = activeClients.get(recipient);
                            PrintWriter recipientOut = new PrintWriter(recipientSocket.getOutputStream(), true);
                            recipientOut.println(input + "\n---END---");
                            recipientOut.flush();
                            System.out.println("Messaggio mandato a: " + recipient);
                        } catch (IOException e) {
                            System.err.println("Errore nell'invio del messaggio a " + recipient + ": " + e.getMessage());
                        }
                    }
                }
                if (recipient != null && activeClients.containsKey(Admin)) {
                    try {
                        Socket adminSocket = activeClients.get(Admin);
                        PrintWriter adminOut = new PrintWriter(adminSocket.getOutputStream(), true);
                        String sender = extractSender(input);
                        String logMsg = "Log: " + sender + " ha mandato una mail a " + recipient;
                        adminOut.println(logMsg + "\n---END---");
                        adminOut.flush();
                        System.out.println("Log inviato all'admin: " + logMsg);
                    } catch (IOException e) {
                        System.err.println("Errore nell'invio del log all'admin: " + e.getMessage());
                    }
                }else{
                    System.out.println("chissa perche è null: " + Admin);
                }
                // Dopo aver rimosso i messaggi inviati, aggiorna il file
                saveAllMessagesToFile();
            }
        } catch (IOException e) {
            System.err.println("Errore nel client handler: " + e.getMessage());
        } finally {
            if(clientEmail != null) {
                activeClients.remove(clientEmail);
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private static String extractRecipient(String message) {
        for (String line : message.split("\n")) {
            if (line.startsWith("A:")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

    private static void saveMessage(String message) {
        synchronized (fileLock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(MESSAGE_FILE, true))) {
                writer.write(message);
                writer.newLine();
                writer.write("---END---");
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Errore nel salvataggio del messaggio: " + e.getMessage());
            }
        }
    }

    private static void loadMessages() {
        File file = new File(MESSAGE_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder currentMessage = new StringBuilder();
            String line;
            // Per semplicità, consideriamo ogni riga come un messaggio completo.
            // Se i messaggi hanno più righe, occorre usare un delimitatore.
            while ((line = reader.readLine()) != null) {
                if (line.equals("---END---")) {
                    String fullMessage = currentMessage.toString().trim();
                    messages.add(fullMessage);
                    currentMessage.setLength(0); // Pulisce per il prossimo messaggio
                } else {
                    currentMessage.append(line).append("\n");
                }
            }
            System.out.println("Messaggio ricevuto e salvato: \n" + messages);
        } catch (IOException e) {
            System.err.println("Errore nel caricamento dei messaggi: " + e.getMessage());
        }
    }

    private static void sendPendingMessages(String clientEmail, PrintWriter out) {
        Iterator<String> iterator = messages.iterator();
        while (iterator.hasNext()) {
            String msg = iterator.next();
            String recipient = extractRecipient(msg);
            if (recipient != null && recipient.equalsIgnoreCase(clientEmail)) {
                System.out.println("Messaggio mandato: \n");
                out.println(msg + "\n---END---");
                out.flush();
            }
        }
        // Dopo aver rimosso i messaggi inviati, aggiorna il file
        saveAllMessagesToFile();
    }

    private static void saveAllMessagesToFile() {
        synchronized (fileLock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(MESSAGE_FILE))) {
                for (String msg : messages) {
                    writer.write(msg);
                    writer.newLine();
                    writer.write("---END---");
                    writer.newLine();
                }
            } catch (IOException e) {
                System.err.println("Errore nel salvataggio dei messaggi: " + e.getMessage());
            }
        }
    }

    private static String extractSender(String message) {
        for (String line : message.split("\n")) {
            if (line.startsWith("DA:")) {
                return line.substring("DA:".length()).trim();
            }
        }
        return "Sconosciuto";
    }

    public static ConcurrentLinkedQueue<String> getMessages() {
        return messages;
    }
}


