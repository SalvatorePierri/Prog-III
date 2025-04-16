package model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ModelAdmin {
    private final StringProperty adminMail = new SimpleStringProperty();
    private final ObservableList<String> adminLogs = FXCollections.observableArrayList();
    private final BooleanProperty connectionActive = new SimpleBooleanProperty(false);

    // Proprietà per l'email dell'admin
    public String getAdminMail() {
        return adminMail.get();
    }
    public void setAdminMail(String mail) {
        adminMail.set(mail);
    }
    public StringProperty adminMailProperty() {
        return adminMail;
    }

    // Lista dei log ricevuti dall'admin
    public ObservableList<String> getAdminLogs() {
        return adminLogs;
    }

    // Stato della connessione al server
    public boolean isConnectionActive() {
        return connectionActive.get();
    }
    public void setConnectionActive(boolean active) {
        connectionActive.set(active);
    }
    public BooleanProperty connectionActiveProperty() {
        return connectionActive;
    }
}
