package model;

import app.GeneralUser;
import app.Message;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

public class ModelClient {

    private final StringProperty userEmail = new SimpleStringProperty();
    private final ObservableList<GeneralUser> utenti = FXCollections.observableArrayList();
    private final ObservableList<Message> inbox = FXCollections.observableArrayList();
    private final BooleanProperty connessioneAttiva = new SimpleBooleanProperty(false);
    private final BooleanProperty emailNonValida = new SimpleBooleanProperty(false);

    // Proprietà per l'email dell'utente
    public String getUserEmail() { return userEmail.get(); }
    public void setUserEmail(String email) { this.userEmail.set(email); }
    public StringProperty userEmailProperty() { return userEmail; }

    // Lista degli utenti registrati
    public ObservableList<GeneralUser> getUtenti() { return utenti; }
    public void setUtenti(List<GeneralUser> listaUtenti) {
        utenti.setAll(listaUtenti);
    }

    // Inbox (lista dei messaggi)
    public ObservableList<Message> getInbox() { return inbox; }

    // Stato della connessione
    public boolean isConnessioneAttiva() { return connessioneAttiva.get(); }
    public void setConnessioneAttiva(boolean attiva) { connessioneAttiva.set(attiva); }
    public BooleanProperty connessioneAttivaProperty() { return connessioneAttiva; }

    // Stato per la validità delle email inserite
    public boolean isEmailNonValida() { return emailNonValida.get(); }
    public void setEmailNonValida(boolean nonValida) { emailNonValida.set(nonValida); }
    public BooleanProperty emailNonValidaProperty() { return emailNonValida; }
}

