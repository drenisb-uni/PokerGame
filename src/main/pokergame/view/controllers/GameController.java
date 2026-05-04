package pokergame.view.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        dealRandomCards();
    }

    @FXML
    public void handleFold(ActionEvent event) {
        dealRandomCards();

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


    // A list of strings representing the paths to your card images
    private final List<String> fakeDeck = new ArrayList<>(Arrays.asList(
            "/images/2-C.png",
            "/images/2-H.png",
            "/images/2-D.png",
            "/images/2-S.png",
            "/images/3-C.png",
            "/images/3-H.png",
            "/images/3-D.png",
            "/images/3-S.png",
            "/images/4-C.png",
            "/images/4-H.png",
            "/images/4-D.png",
            "/images/4-S.png",
            "/images/5-C.png",
            "/images/5-H.png",
            "/images/5-D.png",
            "/images/5-S.png",
            "/images/6-C.png",
            "/images/6-H.png",
            "/images/6-D.png",
            "/images/6-S.png",
            "/images/7-C.png",
            "/images/7-H.png",
            "/images/7-D.png",
            "/images/7-S.png",
            "/images/8-C.png",
            "/images/8-H.png",
            "/images/8-D.png",
            "/images/8-S.png",
            "/images/9-C.png",
            "/images/9-H.png",
            "/images/9-D.png",
            "/images/9-S.png",
            "/images/10-C.png",
            "/images/10-H.png",
            "/images/10-D.png",
            "/images/10-S.png",
            "/images/J-C.png",
            "/images/J-H.png",
            "/images/J-D.png",
            "/images/J-S.png",
            "/images/Q-C.png",
            "/images/Q-H.png",
            "/images/Q-D.png",
            "/images/Q-S.png",
            "/images/K-C.png",
            "/images/K-H.png",
            "/images/K-D.png",
            "/images/K-S.png",
            "/images/A-C.png",
            "/images/A-H.png",
            "/images/A-D.png",
            "/images/A-S.png"
    ));

    public void dealRandomCards() {
        // 1. Magically randomize the list
        Collections.shuffle(fakeDeck);

        // 2. Loop through our 5 UI slots and give them the top 5 cards from the shuffled deck
        for (int i = 0; i < communityCards.length; i++) {
            // Get the path from our randomized deck (index 0 through 4)
            String imagePath = fakeDeck.get(i);

            // Load the image. We use getClass().getResource() to find it safely in the resources folder
            Image cardImage = new Image(getClass().getResource(imagePath).toExternalForm());

            // Set it to the UI
            communityCards[i].setImage(cardImage);
        }
    }
}
