package pokergame.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class SceneManager {

    private static Stage mainStage;

    public static void setMainStage(Stage stage) {
        mainStage = stage;
    }
    public static void switchScene(String fxmlFileName) {
        try {
            URL fxmlLocation = SceneManager.class.getResource("/fxml/" + fxmlFileName);

            if (fxmlLocation == null) {
                throw new RuntimeException("CRITICAL ERROR: Could not find " + fxmlFileName);
            }

            Parent root = FXMLLoader.load(fxmlLocation);

            Scene newScene = new Scene(root, 440-100, 956-300);

            mainStage.setScene(newScene);
            mainStage.show();

        } catch (IOException e) {
            System.err.println("Failed to load scene: " + fxmlFileName);
            e.printStackTrace();
        }
    }
}