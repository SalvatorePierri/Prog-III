package controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import model.ModelAdmin;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class AdminController {

    // Utilizzo del ModelAdmin per mantenere lo stato dell'admin
    private final ModelAdmin model = new ModelAdmin();

    @FXML
    private ListView<String> adminLogArea;
    @FXML
    private Circle Cerchio;

    @FXML
    public void initialize() {
        controllaConnessionePeriodicamente();
        startAdminMessageListener();
    }

    private void startAdminMessageListener() {
        Thread listener = new Thread(() -> {
            while (true) {
                try (Socket socket = new Socket("localhost", 1234);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    // Invia la registrazione dell'admin con il ruolo
                    out.println("CLIENT:" + model.getAdminMail() + " role: admin");
                    out.println("---END---");

                    StringBuilder messageBuilder = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.equals("---END---")) {
                            String completeMessage = messageBuilder.toString().trim();
                            // Controllo se il messaggio è un log
                            if (completeMessage.startsWith("Log:")) {
                                Platform.runLater(() -> {
                                    adminLogArea.getItems().add(completeMessage + "\n");
                                    model.getAdminLogs().add(completeMessage);
                                });
                            }
                            messageBuilder.setLength(0);
                        } else {
                            messageBuilder.append(line).append("\n");
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Errore nella connessione admin, riprovo: " + e.getMessage());
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        // Gestione dell'interruzione
                    }
                }
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    // Metodo per impostare l'email dell'admin nel model
    public void setUserMail(String mail) {
        model.setAdminMail(mail);
    }

    private void controllaConnessionePeriodicamente() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> verificaConnessione())
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void verificaConnessione() {
        try (Socket socket = new Socket("localhost", 1234)) {
            Cerchio.setStyle("-fx-fill: green;");
            model.setConnectionActive(true);
        } catch (ConnectException ex) {
            Cerchio.setStyle("-fx-fill: red;");
            model.setConnectionActive(false);
        } catch (IOException e) {
            Cerchio.setStyle("-fx-fill: red;");
            model.setConnectionActive(false);
        }
    }
}
