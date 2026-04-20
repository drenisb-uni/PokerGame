package pokergame.view.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;

public class GameController {

    @FXML
    private ImageView commCard1;
    @FXML
    private ImageView commCard2;
    @FXML
    private ImageView commCard3;
    @FXML
    private ImageView commCard4;
    @FXML
    private ImageView commCard5;

    private ImageView[] communityCards;

    @FXML
    public void initialize() {
        communityCards = new ImageView[]{ commCard1, commCard2, commCard3, commCard4, commCard5 };
    }

    @FXML
    public void handleFold(ActionEvent event) {

    }
    @FXML
    public void handleCall(ActionEvent event) {

    }
    @FXML
    public void showRaisePopup(ActionEvent event) {

    }

    @FXML
    public void handleAllIn(ActionEvent event) {

    }
    @FXML
    public void handleConfirmRaise(ActionEvent event) {

    }
    @FXML
    public void hideRaisePopup(ActionEvent event) {

    }
}
