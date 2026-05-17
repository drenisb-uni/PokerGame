package pokergame.view.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import pokergame.view.SceneManager;

public class LobbyController {

    @FXML
    public void handleProfile(ActionEvent event) {
        System.out.println("Opening Profile...");
    }

    @FXML
    public void handlePlayNow(ActionEvent event) {
        SceneManager.switchScene("GameTable.fxml");
    }

    @FXML
    public void handleJoinTable(ActionEvent event) {
        SceneManager.switchScene("GameTable.fxml");
    }
}
