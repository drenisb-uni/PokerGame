package pokergame.view.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import pokergame.view.SceneManager;

public class WelcomeController {
    @FXML
    public void onStartButtonClicked(ActionEvent event) {
        System.out.println("Start Game button was clicked!");
        SceneManager.switchScene("Login.fxml");
    }
}