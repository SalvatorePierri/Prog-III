package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = fxmlLoader.load();
        primaryStage.setTitle("Login Page");
        primaryStage.setScene(new Scene(root, 618, 416)); // Dimensioni prese dal FXML
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
