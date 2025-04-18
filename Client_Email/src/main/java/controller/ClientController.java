package controller;

import app.GeneralUser;
import app.Message;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.ModelClient;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class ClientController {

    @FXML private TextField CoSender;
    @FXML private AnchorPane MainArea;
    @FXML private Label TopLabel;
    @FXML private ListView<Message> list;
    @FXML private Label EmailError;
    @FXML private Button SendButton;
    @FXML private TextArea MailText;
    @FXML private TextField Receiver;
    @FXML private Button CloseMail;
    @FXML private Button SendMail;
    @FXML private AnchorPane Mail;
    @FXML private Circle Connesso;
    @FXML private AnchorPane ForwardArea;
    @FXML private Button SendForward;
    @FXML private Button CloseForward;
    @FXML private TextField TextForward;

    // Il Model che contiene lo stato dell'applicazione
    private final ModelClient model = new ModelClient();
    private boolean mailVisible = false;
    private Socket listenerSocket;
    private Thread listenerThread;
    final String EMAIL_REGEX = "^$|^([A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})(,\\s*[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})*$";

    ChangeListener<String> lockReplyHeaderListener = (obs, oldText, newText) -> {
            String prefix = "---- Reply ----\n";
            if (!newText.startsWith(prefix)) {
                MailText.setText(oldText); // Torna al testo precedente se prova a modificare l’intestazione
                MailText.positionCaret(oldText.length()); // Rimetti il cursore in fondo
            }
    };

    ChangeListener<String> lookForEmailFormat = (obs, oldText, newText) -> {
            if (newText.matches(EMAIL_REGEX)) {
                model.setEmailNonValida(false);
            }else{
                model.setEmailNonValida(true);
            }
    };

    @FXML
    public void initialize() {
        bindModelToView();

        Platform.runLater(() -> {
            Stage stage = (Stage) MainArea.getScene().getWindow(); // Mail è già nel tuo FXML
            stage.setOnCloseRequest(event -> {
                closeConnection();  // chiude il socket all'uscita
            });
        });

        // Avvio dei thread e dei controlli periodici
        controllaConnessionePeriodicamente();
        startMessageListener();

        // Gestione degli eventi dei bottoni per mostrare/nascondere l'area mail
        SendMail.setOnAction(e -> {
            Receiver.textProperty().addListener(lookForEmailFormat);
            CoSender.textProperty().addListener(lookForEmailFormat);
            MailText.textProperty().removeListener(lockReplyHeaderListener);
            Receiver.setDisable(false);
            Receiver.clear();
            CoSender.clear();
            MailText.clear();
            TextForward.clear();
            toggleMailArea();
        });
        CloseMail.setOnAction(e -> toggleMailArea());

        // Invia email dal bottone "Send"
        SendButton.setOnAction(e -> {
            try {
                sendMail(Receiver.getText());
            } catch (IOException ex) {
                showError(ex);
            }
        });

        // Impostazione degli eventi per l'area Forward
        SendForward.setOnAction(e -> {
            try {
                sendMail(TextForward.getText());
                ForwardArea.setVisible(false);
            } catch (IOException ex) {
                showError(ex);
            }
        });
        CloseForward.setOnAction(e -> ForwardArea.setVisible(false));

        // Configurazione della ListView per visualizzare i messaggi
        list.setItems(model.getInbox());
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Message item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox mainHBox = new HBox();
                    mainHBox.setSpacing(10);
                    mainHBox.setAlignment(Pos.CENTER_LEFT);

                    Label messageLabel = new Label(item.getFirstLine());
                    messageLabel.setMaxWidth(1100);
                    messageLabel.setWrapText(true);
                    HBox.setHgrow(messageLabel, Priority.ALWAYS);

                    // Contenitore per i bottoni con padding simile a quello originale (margine sinistro = 880)
                    HBox buttonsContainer = new HBox(5);
                    buttonsContainer.setAlignment(Pos.CENTER_RIGHT);
                    buttonsContainer.setMaxWidth(150);
                    buttonsContainer.setMinWidth(150);

                    VBox buttonsVBox = new VBox(5);
                    buttonsVBox.setId("vbox");

                    Button forward = new Button("Forward");
                    forward.setId("forward");
                    forward.setOnAction(e -> forwardMessage(item));

                    Button cancel = new Button("Cancel");
                    cancel.setId("cancel");
                    cancel.setOnAction(e -> {
                        try (Socket socket = new Socket("localhost", 1234);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                            // Invia al server una richiesta di cancellazione
                            out.println("DELETE_MESSAGE\n" + item.getFullMessage());
                            out.println("---END---");
                            // Rimuove il messaggio dalla lista locale
                            getListView().getItems().remove(item);
                        } catch (IOException ex) {
                            System.out.println("Errore durante la cancellazione: " + ex.getMessage());
                            Errore();
                        }
                    });

                    Button reply = new Button("Reply");
                    reply.setId("reply");
                    reply.setVisible(false);
                    reply.setOnAction(e -> {
                        if(!mailVisible) {
                            // Azioni per il reply...
                            String aux = item.getFirstLine();
                            MailText.setText("---- Reply ----\n");
                            aux = aux.substring(aux.indexOf(": ") + 2);
                            aux = aux.substring(0, aux.indexOf(" E: "));
                            Receiver.setText(aux);
                            Receiver.setDisable(true);
                            toggleMailArea();
                            MailText.textProperty().addListener(lockReplyHeaderListener);
                        }
                    });

                    Button replyAll = new Button("ReplyAll");
                    replyAll.setId("replyAll");
                    replyAll.setVisible(false);
                    replyAll.setOnAction(e -> {
                        if(!mailVisible) {
                            // Azioni per il reply...
                            String aux = item.getFirstLine();
                            MailText.setText("---- Reply ----\n");
                            aux = aux.substring(aux.indexOf(": ") + 2);
                            aux = aux.replace(" E: ", ", ");
                            Receiver.setText(aux);
                            Receiver.setDisable(true);
                            toggleMailArea();
                            MailText.textProperty().addListener(lockReplyHeaderListener);
                        }
                    });

                    buttonsVBox.getChildren().addAll(forward, cancel, reply, replyAll);
                    buttonsContainer.getChildren().add(buttonsVBox);

                    mainHBox.getChildren().addAll(messageLabel, buttonsContainer);
                    setGraphic(mainHBox);

                    // Gestione del click sul messaggio per alternare tra anteprima e messaggio completo
                    messageLabel.setOnMouseClicked(event -> {
                        if (messageLabel.getText().equals(item.getFirstLine())) {
                            messageLabel.setText(item.getFullMessage());
                            reply.setVisible(true);
                            replyAll.setVisible(true);
                        } else {
                            reply.setVisible(false);
                            replyAll.setVisible(false);
                            messageLabel.setText(item.getFirstLine());
                        }
                    });
                }
            }
        });
    }

    /** Effettua il binding delle proprietà del model con alcuni elementi della view */
    private void bindModelToView() {
        TopLabel.textProperty().bind(model.userEmailProperty());
        EmailError.visibleProperty().bind(model.emailNonValidaProperty());
        model.connessioneAttivaProperty().addListener((obs, oldVal, newVal) ->
                Connesso.setStyle("-fx-fill: " + (newVal ? "green;" : "red;"))
        );
    }

    public void setUserMail(String mail) {
        model.setUserEmail(mail);
    }

    public void setUserUtenti(java.util.List<GeneralUser> utenti) {
        model.setUtenti(utenti);
    }

    /** Alterna la visibilità dell'area per comporre il messaggio */
    private void toggleMailArea() {
        mailVisible = !mailVisible;
        Mail.setVisible(mailVisible);
    }

    /** Alterna la visibilità dell'area Forward */
    private void toggleForwardArea() {
        ForwardArea.setVisible(!ForwardArea.isVisible());
    }

    /** Gestisce l'invio del messaggio a uno o più destinatari */
    private void sendMail(String rmail) throws IOException {
        String[] recipientList = rmail.split("[,;\\s]+");
        for (String recipient : recipientList) {
            String trimmedRecipient = recipient.trim();
            if (trimmedRecipient.isEmpty()) continue;

            // Controllo se l'utente destinatario esiste
            boolean found = false;
            for (GeneralUser utente : model.getUtenti()) {
                if (utente.getEmail().equalsIgnoreCase(trimmedRecipient)) {
                    model.setEmailNonValida(false);
                    found = true;
                    // Se c'è del testo da inviare, apri una connessione e invia il messaggio
                    if (!MailText.getText().isEmpty()) {
                        try (Socket socket = new Socket("localhost", 1234);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                            if(CoSender.getText().isEmpty() || CoSender.getText().isBlank() || CoSender == null) {
                                out.println("DA: " + model.getUserEmail() + "\nA: " + utente.getEmail() + "\n" + MailText.getText());
                            }else{
                                out.println("DA: " + model.getUserEmail() + " E: " + CoSender.getText() + "\nA: " + utente.getEmail() + "\n" + MailText.getText());
                            }
                        } catch (IOException ex) {
                            System.out.println("Errore nell'invio: " + ex.getMessage());
                            Errore();
                        }
                    }
                    // Nasconde l'area mail dopo l'invio
                    Mail.setVisible(false);
                    mailVisible = false;
                }
            }
            ForwardArea.setVisible(false);
            if (!found) {
                emailNotFound();
            }
        }
        // Pulizia dei campi di testo
        MailText.textProperty().removeListener(lockReplyHeaderListener);
        CoSender.clear();
        Receiver.setDisable(false);
        Receiver.clear();
        MailText.clear();
        TextForward.clear();
    }

    private void emailNotFound() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Errore Email");
        alert.setHeaderText(null); // puoi rimuovere o personalizzare l'intestazione
        alert.setContentText("Email non esistente!");
        alert.show();
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void Errore() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Errore 404");
        alert.setHeaderText(null); // puoi rimuovere o personalizzare l'intestazione
        alert.setContentText("Server Error - 404");
        alert.show();
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    /** Gestisce l'azione del pulsante "Forward" all'interno di una cella della ListView */
    private void forwardMessage(Message item) {
        MailText.setText("---- Forwarded Message ----\n" + item.getFullMessage());
        toggleForwardArea();
    }

    /** Controlla periodicamente lo stato della connessione al server */
    private void controllaConnessionePeriodicamente() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> verificaConnessione())
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    /** Verifica la connessione aprendo un socket e aggiorna lo stato nel Model */
    private void verificaConnessione() {
        try (Socket socket = new Socket("localhost", 1234)) {
            model.setConnessioneAttiva(true);
            Connesso.setStyle("-fx-fill: green;");
        } catch (ConnectException ex) {
            model.setConnessioneAttiva(false);
            Connesso.setStyle("-fx-fill: red;");
        } catch (IOException e) {
            model.setConnessioneAttiva(false);
            Connesso.setStyle("-fx-fill: red;");
        }
    }

    /** Avvia il thread che ascolta i messaggi dal server */
    private void startMessageListener() {
        listenerThread = new Thread(() -> {
            while (true) {
                try {
                    listenerSocket = new Socket("localhost", 1234);
                    try (PrintWriter out = new PrintWriter(listenerSocket.getOutputStream(), true);
                         BufferedReader in = new BufferedReader(new InputStreamReader(listenerSocket.getInputStream()))) {

                        out.println("CLIENT:" + model.getUserEmail());
                        out.println("---END---");

                        StringBuilder messageBuilder = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.equals("---END---")) {
                                String completeMessage = messageBuilder.toString().trim();
                                Platform.runLater(() -> model.getInbox().add(0, new Message(completeMessage)));
                                messageBuilder.setLength(0);
                            } else {
                                messageBuilder.append(line).append("\n");
                            }
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Errore di connessione, riprovo... (" + e.getMessage() + ")");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /** Stampa l'errore (eventualmente da sostituire con una notifica all'utente) */
    private void showError(Exception e) {
        System.out.println("Errore: " + e.getMessage());
    }

    public void closeConnection() {
        try {
            if (listenerSocket != null && !listenerSocket.isClosed()) {
                listenerSocket.close(); // Questo fa uscire il ciclo lato server
            }
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.interrupt(); // opzionale
            }
            System.out.println("Connessione chiusa dal client");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
