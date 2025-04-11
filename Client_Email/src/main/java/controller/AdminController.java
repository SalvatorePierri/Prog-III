package controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class AdminController {

    String mail;

    @FXML
    private ListView adminLogArea;
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

                    // Invia la registrazione (assicurati di aver inviato anche il ruolo admin al momento della connessione)
                    out.println("CLIENT:" + mail + " role: admin");
                    out.println("---END---");

                    StringBuilder messageBuilder = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.equals("---END---")) {
                            String completeMessage = messageBuilder.toString().trim();
                            // Controllo se il messaggio è un log
                            if (completeMessage.startsWith("Log:")) {
                                Platform.runLater(() -> adminLogArea.getItems().add(completeMessage + "\n"));
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

    public void setUserMail(String mail) {
        this.mail = mail;
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
        } catch (ConnectException ex) {
            Cerchio.setStyle("-fx-fill: red;");
        } catch (IOException e) {
            Cerchio.setStyle("-fx-fill: red;");
        }
    }


}

