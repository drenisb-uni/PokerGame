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
//
//        URL fxmlLocation = getClass().getResource("/fxml/Lobby.fxml");
//
//        if (fxmlLocation == null) {
//            System.err.println("CRITICAL ERROR: Could not find Lobby.fxml!");
//            System.err.println("Make sure it is in src/main/resources/fxml/");
//            System.exit(1);
//        }
//
//        Parent root = FXMLLoader.load(fxmlLocation);
//        Scene scene = new Scene(root, 800, 600);
//
//        stage.setTitle("Texas Hold'em Poker - MVC Engine");
//        stage.setScene(scene);
//        stage.setResizable(false);

        Circle circle1 = new Circle(30, Color.RED);
        Circle circle2 = new Circle(30, Color.BLUE);
        Circle circle3 = new Circle(30, Color.GREEN);
        Circle circle4 = new Circle(30, Color.GOLD);

        HBox pane1 = new HBox(10);
        HBox pane2 = new HBox(10);

        pane1.getChildren().addAll(circle1, circle2, circle3, circle4);
        pane2.getChildren().addAll(circle1, circle2, circle3, circle4);

        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(pane1, pane2);

        Scene scene = new Scene(vbox);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}