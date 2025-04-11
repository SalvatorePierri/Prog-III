module com.example.client_email {
    requires javafx.controls;
    requires javafx.fxml;

    exports app;  // Esporta il pacchetto 'app' per MainApp
    exports controller;  // Esporta il pacchetto 'controller' se lo stai utilizzando come pacchetto per il controller

    opens controller to javafx.fxml;  // Apre il pacchetto 'controller' per la riflessione a javafx.fxml
    opens fxml to javafx.fxml;
    opens app to javafx.fxml;
    exports model;
    opens model to javafx.fxml;  // Se il pacchetto fxml deve essere aperto
}