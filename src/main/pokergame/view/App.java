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
import pokergame.GameContext;
import pokergame.dbinfrastructure.HikariDSProvider;
import pokergame.dbinfrastructure.SqlPlayerRepository;

import java.net.URL;

public class App extends Application {
    GameContext gameContext;

    @Override
    public void start(Stage stage) throws Exception {
        javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/fxml/fonts/Galada/Galada-Regular.ttf"), 14);

        HikariDSProvider ds = new HikariDSProvider();
        SqlPlayerRepository sqlPlayerRepository = new SqlPlayerRepository((pokergame.dbinfrastructure.HikariDSProvider) ds);
        GameContext.setPlayerRepository(sqlPlayerRepository);

        SceneManager.setMainStage(stage);
        stage.setTitle("Texas Hold'em Poker - MVC Engine");
        stage.setResizable(false);
        SceneManager.switchScene("Login.fxml");
    }

    public static void main(String[] args) {
        launch(args);
    }
}