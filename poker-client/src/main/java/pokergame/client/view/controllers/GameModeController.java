package pokergame.client.view.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import pokergame.client.view.SceneManager;

public class GameModeController {

    @FXML
    void handlePlayWithBots(ActionEvent event) {
        SceneManager.switchScene("BotConfiguration.fxml");
    }

    @FXML
    void handlePlayWithFriends(ActionEvent event) {
        SceneManager.switchScene("Lobby.fxml");
    }
}