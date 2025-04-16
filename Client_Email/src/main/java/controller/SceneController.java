package controller;
import app.GeneralUser;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SceneController {
    @FXML
    private CheckBox checkBox;

    @FXML
    private TextField emailField;

    @FXML
    private Button Signin;

    @FXML
    private Label errorLabel;

    @FXML
    private void initialize() {
        System.out.println("FXML Caricato correttamente!");
        Signin.setDisable(true);
        Check();
        Signin.setOnAction(event -> {
            try {
                handleLogin(event);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void Check(){
        final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        emailField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.matches(EMAIL_REGEX)) {
                errorLabel.setVisible(false);
                checkBox.selectedProperty().addListener((observable1,oldValue1,newValue1) -> {
                    if (checkBox.isSelected()) {
                        Signin.setDisable(false);
                    }else{
                        Signin.setDisable(true);
                    }
                });
                if (checkBox.isSelected()) {
                    Signin.setDisable(false);
                }
            }else{
                Signin.setDisable(true);
                if (checkBox.isSelected()) {
                    errorLabel.setVisible(true);
                    Signin.setDisable(false);
                }
            }
        });
    }

    private void handleLogin(ActionEvent event) throws IOException {
        List<GeneralUser> utenti = new ArrayList<>();
        utenti.add(new GeneralUser("Simonepanetti@gmail.com", GeneralUser.role.User));
        utenti.add(new GeneralUser("Salvatorepierri@gmail.com", GeneralUser.role.Admin));
        utenti.add(new GeneralUser("Giacomoporetti@yahoo.it", GeneralUser.role.User));
        utenti.add(new GeneralUser("Lauraspadaro22@hotmail.net", GeneralUser.role.User));

        String email = emailField.getText();
        for(GeneralUser utente : utenti){
            if(utente.getEmail().equalsIgnoreCase(email)){
                if(utente.getRole() == GeneralUser.role.User){
                    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/EmailClient.fxml"));
                    Parent root = fxmlLoader.load();
                    ClientController clientController = fxmlLoader.getController();
                    clientController.setUserMail(utente.getEmail());
                    clientController.setUserUtenti(utenti);
                    Stage userStage = new Stage();
                    userStage.setTitle("Email Client");
                    userStage.setScene(new Scene(root, 1568, 900));
                    userStage.show();
                    Stage primaryStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                    primaryStage.close();
                }else if(utente.getRole() == GeneralUser.role.Admin){
                    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/EmailAdmin.fxml"));
                    Parent root = fxmlLoader.load();
                    AdminController adminController = fxmlLoader.getController();
                    adminController.setUserMail(utente.getEmail());
                    Stage userStage = new Stage();
                    userStage.setTitle("Email Admin");
                    userStage.setScene(new Scene(root, 1568, 900));
                    userStage.show();
                    Stage primaryStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                    primaryStage.close();
                }
            }
        }
        errorLabel.setText("Invalid email address");
        errorLabel.setVisible(true);
    }
}
