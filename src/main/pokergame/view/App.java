package pokergame.view;

import javafx.application.Application;
import javafx.stage.Stage;

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