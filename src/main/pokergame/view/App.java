package pokergame.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import javafx.scene.shape.Polygon;
import javafx.scene.paint.Color;
import java.net.URL;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        SceneManager.setMainStage(stage);
        stage.setTitle("Texas Hold'em Poker - MVC Engine");
        stage.setResizable(false);
        SceneManager.switchScene("Welcome.fxml");
    }

    public static void main(String[] args) {
        launch(args);
    }
}