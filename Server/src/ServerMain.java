import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.*;

public class ServerMain {
    private static final Object fileLock = new Object();
    private static final int PORT = 1234;
    private static final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
    private static final Map<String, Socket> activeClients = new ConcurrentHashMap<>();
    private static final String MESSAGE_FILE = "messages.txt";
    private static final String DELIVERED_DIR = "delivered";
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
            // Crea la directory per i messaggi consegnati se non esiste
            new File(DELIVERED_DIR).mkdirs();

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
                    if (activeClients.containsKey(clientEmail)) {
                        Socket vecchioSocket = activeClients.get(clientEmail);
                        try {
                            if (!vecchioSocket.isClosed()) {
                                vecchioSocket.close();
                                System.out.println("Vecchia connessione chiusa per: " + clientEmail);
                            }
                        } catch (IOException e) {
                            System.err.println("Errore nella chiusura della vecchia connessione per: " + clientEmail);
                        }
                        activeClients.remove(clientEmail);
                    }
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
                    System.out.println("elimino in: " + clientEmail);
                    if(clientEmail != null) {
                        activeClients.remove(clientEmail);
                        // Elimina il file del client quando si disconnette
                        File clientFile = new File(DELIVERED_DIR, clientEmail + ".txt");
                        if (clientFile.exists()) {
                            if (clientFile.delete()) {
                                System.out.println("File del client eliminato: " + clientFile.getName());
                            } else {
                                System.err.println("Impossibile eliminare il file del client: " + clientFile.getName());
                            }
                        }
                    }
                }
            } else {
                if (input.startsWith("DELETE_MESSAGE")) {
                    // Estrai il messaggio da cancellare (senza il comando DELETE_MESSAGE)
                    String messageToDelete = input.substring("DELETE_MESSAGE\n".length());
                    System.out.println("Ricevuta richiesta di cancellazione per il messaggio:\n" + messageToDelete);
                    deleteMessage(messageToDelete);
                    System.out.println("Messaggio cancellato");
                } else {
                    saveMessage(input);
                    messages.add(input);
                    System.out.println("Messaggio ricevuto e salvato: \n" + input);
                    String recipient = extractRecipient(input);
                    if (recipient != null) {
                        recipient = recipient.toLowerCase();
                        if (activeClients.containsKey(recipient)) {
                            try {
                                // Controlla se il messaggio è già stato consegnato
                                String messageId = generateMessageId(input);
                                Set<String> deliveredMessages = loadDeliveredMessages(recipient);

                                if (!deliveredMessages.contains(messageId)) {
                                    Socket recipientSocket = activeClients.get(recipient);
                                    PrintWriter recipientOut = new PrintWriter(recipientSocket.getOutputStream(), true);
                                    recipientOut.println(input + "\n---END---");
                                    recipientOut.flush();
                                    System.out.println("Messaggio mandato a: " + recipient);

                                    // Segna il messaggio come consegnato
                                    markMessageAsDelivered(recipient, messageId);
                                } else {
                                    System.out.println("Messaggio già consegnato a: " + recipient);
                                }
                            } catch (IOException e) {
                                System.err.println("Errore nell'invio del messaggio a " + recipient + ": " + e.getMessage());
                            }
                        }
                    }
                    if (recipient != null && Admin != null && activeClients.containsKey(Admin)) {
                        try {
                            Socket adminSocket = activeClients.get(Admin);
                            if (adminSocket != null && !adminSocket.isClosed()) {
                                PrintWriter adminOut = new PrintWriter(adminSocket.getOutputStream(), true);
                                String sender = extractSender(input);
                                String logMsg = "Log: " + sender + " ha mandato una mail a " + recipient;
                                adminOut.println(logMsg + "\n---END---");
                                adminOut.flush();
                                System.out.println("Log inviato all'admin: " + logMsg);
                            }
                        } catch (IOException e) {
                            System.err.println("Errore nell'invio del log all'admin: " + e.getMessage());
                            // Rimuovi l'admin se la connessione è persa
                            if (Admin != null) {
                                activeClients.remove(Admin);
                                Admin = null;
                            }
                        }
                    } else {
                        if (Admin == null) {
                            System.out.println("Nessun admin connesso");
                        } else {
                            System.out.println("Admin non raggiungibile: " + Admin);
                        }
                    }
                    // Dopo aver rimosso i messaggi inviati, aggiorna il file
                    saveAllMessagesToFile();
                }
            }
        } catch (IOException e) {
            System.err.println("Errore nel client handler: " + e.getMessage());
        } finally {
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
        // Carica i messaggi già consegnati per questo client
        Set<String> deliveredMessages = loadDeliveredMessages(clientEmail);

        Iterator<String> iterator = messages.iterator();
        while (iterator.hasNext()) {
            String msg = iterator.next();
            String recipient = extractRecipient(msg);
            if (recipient != null && recipient.equalsIgnoreCase(clientEmail)) {
                // Calcola un ID univoco per il messaggio
                String messageId = generateMessageId(msg);

                // Invia il messaggio solo se non è stato già consegnato
                if (!deliveredMessages.contains(messageId)) {
                    System.out.println("Messaggio mandato: \n" + msg);
                    out.println(msg + "\n---END---");
                    out.flush();

                    // Segna il messaggio come consegnato
                    markMessageAsDelivered(clientEmail, messageId);
                }
            }
        }
    }

    private static Set<String> loadDeliveredMessages(String clientEmail) {
        Set<String> delivered = new HashSet<>();
        File file = new File(DELIVERED_DIR, clientEmail + ".txt");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    delivered.add(line.trim());
                }
            } catch (IOException e) {
                System.err.println("Errore nel caricamento dei messaggi consegnati: " + e.getMessage());
            }
        }
        return delivered;
    }

    private static void markMessageAsDelivered(String clientEmail, String messageId) {
        File file = new File(DELIVERED_DIR, clientEmail + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(messageId);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Errore nel salvare il messaggio consegnato: " + e.getMessage());
        }
    }

    private static String generateMessageId(String message) {
        try {
            // Crea un ID univoco basato sul contenuto del messaggio
            String sender = extractSender(message);
            String recipient = extractRecipient(message);
            
            // Trova l'indice dopo la seconda riga (dopo "A: recipient")
            int firstNewline = message.indexOf("\n");
            if (firstNewline == -1) return message.hashCode() + "";
            
            int secondNewline = message.indexOf("\n", firstNewline + 1);
            if (secondNewline == -1) return message.hashCode() + "";
            
            String content = message.substring(secondNewline + 1).trim();
            return String.format("%s_%s_%d", 
                sender != null ? sender : "unknown",
                recipient != null ? recipient : "unknown",
                content.hashCode());
        } catch (Exception e) {
            // In caso di errore, usa l'hash dell'intero messaggio
            System.err.println("Errore nella generazione del messageId: " + e.getMessage());
            return String.valueOf(message.hashCode());
        }
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

    private static void deleteMessage(String messageContent) {
        synchronized (fileLock) {
            // Rimuovi il messaggio dalla coda in memoria
            String messageIdToDelete = null;
            boolean removed = messages.removeIf(msg -> {
                // Rimuovi eventuali spazi e newline alla fine del messaggio per il confronto
                String cleanMsg = msg.trim();
                String cleanContent = messageContent.trim();
                System.out.println("Confronto:\nMessaggio in coda:\n" + cleanMsg + "\nMessaggio da eliminare:\n" + cleanContent);
                
                if (cleanMsg.equals(cleanContent)) {
                    return true;
                }
                return false;
            });
            
            if (removed) {
                // Genera l'ID del messaggio da eliminare
                messageIdToDelete = generateMessageId(messageContent.trim());
                
                // Rimuovi l'ID del messaggio dai file dei client nella cartella delivered
                File deliveredDir = new File(DELIVERED_DIR);
                if (deliveredDir.exists() && deliveredDir.isDirectory()) {
                    for (File file : deliveredDir.listFiles()) {
                        if (file.isFile() && file.getName().endsWith(".txt")) {
                            removeMessageIdFromFile(file, messageIdToDelete);
                        }
                    }
                }
                
                // Aggiorna il file dei messaggi
                saveAllMessagesToFile();
                System.out.println("File messages.txt aggiornato");
            } else {
                System.out.println("Nessun messaggio corrispondente trovato");
            }
        }
    }

    private static void removeMessageIdFromFile(File file, String messageIdToRemove) {
        try {
            List<String> lines = new ArrayList<>();
            // Leggi tutte le linee tranne quella da rimuovere
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().equals(messageIdToRemove)) {
                        lines.add(line);
                    }
                }
            }
            
            // Riscrivi il file senza l'ID rimosso
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Errore durante la rimozione dell'ID del messaggio dal file " + file.getName() + ": " + e.getMessage());
        }
    }

    public static ConcurrentLinkedQueue<String> getMessages() {
        return messages;
    }
}
