package pokergame.client.view.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import pokergame.GameContext;
import pokergame.client.network.ProfileNetworkClient;
import pokergame.client.network.PokerWebSocketClient;
import pokergame.client.view.SceneManager;
import pokergame.domain.dto.PlayerHandResultDTO;
import pokergame.domain.dto.PlayerProfileDTO;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlayerProfileController {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter HAND_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d HH:mm");

    @FXML private Label usernameTitleLabel;
    @FXML private Label emailTitleLabel;
    @FXML private Label bankrollLabel;
    @FXML private Label netProfitLabel;
    @FXML private Label recordLabel;
    @FXML private Label createdAtLabel;
    @FXML private Label usernameValueLabel;
    @FXML private Label emailValueLabel;
    @FXML private Label playerIdLabel;
    @FXML private Label bankrollValueLabel;
    @FXML private ListView<String> recentTablesList;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        PlayerProfileDTO profile = GameContext.getPlayerProfile();
        if (profile == null) {
            showEmptyProfile();
            return;
        }

        String email = safeText(profile.email(), "No email saved");
        String bankroll = "$" + formatMoney(profile.totalBankroll());

        usernameTitleLabel.setText(safeText(profile.username(), "Player"));
        emailTitleLabel.setText(email);
        bankrollLabel.setText(bankroll);
        netProfitLabel.setText("$0");
        recordLabel.setText("0W / 0L");
        createdAtLabel.setText(profile.createdAt() == null ? "Unknown" : profile.createdAt().format(DATE_FORMAT));

        usernameValueLabel.setText(safeText(profile.username(), "-"));
        emailValueLabel.setText(email);
        playerIdLabel.setText(safeText(profile.id(), "-"));
        bankrollValueLabel.setText(bankroll);
        statusLabel.setText("Profile loaded from your current session.");
        refreshProfile(profile.id());
        loadRecentTables(profile.id());
    }

    @FXML
    public void handleBackToLobby() {
        SceneManager.switchScene("Lobby.fxml");
    }

    @FXML
    public void handleLogout() {
        PokerWebSocketClient client = PokerWebSocketClient.getInstance();
        if (client != null && client.isOpen()) {
            client.close();
        }
        SceneManager.switchScene("Login.fxml");
    }

    private void showEmptyProfile() {
        usernameTitleLabel.setText("No player loaded");
        emailTitleLabel.setText("Log in to see your profile");
        bankrollLabel.setText("$0");
        netProfitLabel.setText("$0");
        recordLabel.setText("0W / 0L");
        createdAtLabel.setText("Unknown");
        usernameValueLabel.setText("-");
        emailValueLabel.setText("-");
        playerIdLabel.setText("-");
        bankrollValueLabel.setText("-");
        if (recentTablesList != null) {
            recentTablesList.getItems().setAll("No recent tables yet.");
        }
        statusLabel.setText("No active profile was found.");
    }

    private void loadRecentTables(String playerId) {
        if (recentTablesList == null) {
            return;
        }

        recentTablesList.getItems().setAll("Loading recent tables...");
        CompletableFuture
                .supplyAsync(() -> new ProfileNetworkClient().loadRecentHands(playerId, 25))
                .thenAccept(results -> Platform.runLater(() -> showRecentTables(results)));
    }

    private void refreshProfile(String playerId) {
        CompletableFuture
                .supplyAsync(() -> new ProfileNetworkClient().loadProfile(playerId))
                .thenAccept(profile -> Platform.runLater(() -> showProfileRefresh(profile)));
    }

    private void showProfileRefresh(PlayerProfileDTO profile) {
        if (profile == null) {
            return;
        }

        GameContext.setPlayerProfile(profile);
        String bankroll = "$" + formatMoney(profile.totalBankroll());
        bankrollLabel.setText(bankroll);
        bankrollValueLabel.setText(bankroll);
        emailTitleLabel.setText(safeText(profile.email(), "No email saved"));
        emailValueLabel.setText(safeText(profile.email(), "-"));
    }

    private void showRecentTables(List<PlayerHandResultDTO> results) {
        if (results == null || results.isEmpty()) {
            recentTablesList.getItems().setAll("No recent tables yet.");
            netProfitLabel.setText("$0");
            recordLabel.setText("0W / 0L");
            statusLabel.setText("No completed games have been saved for this player yet.");
            return;
        }

        int net = results.stream().mapToInt(PlayerHandResultDTO::netProfit).sum();
        long wins = results.stream().filter(PlayerHandResultDTO::winner).count();
        long losses = results.stream().filter(result -> !result.winner() && result.netProfit() < 0).count();
        long even = results.size() - wins - losses;

        netProfitLabel.setText(formatSignedMoney(net));
        recordLabel.setText(wins + "W / " + losses + "L" + (even > 0 ? " / " + even + "E" : ""));
        statusLabel.setText("Showing your last " + results.size() + " saved game" + (results.size() == 1 ? "." : "s."));

        recentTablesList.getItems().setAll(results.stream()
                .map(this::formatRecentTable)
                .toList());
    }

    private String formatRecentTable(PlayerHandResultDTO result) {
        String date = result.playedAt() == null ? "Unknown time" : result.playedAt().format(HAND_DATE_FORMAT);
        String tableName = safeText(result.tableName(), "Table");
        String rank = safeText(result.winningHandRank(), "No showdown");
        String profit = formatSignedMoney(result.netProfit());
        String outcome = result.winner() ? "Won" : result.netProfit() >= 0 ? "Even" : "Lost";
        return date + "  |  " + outcome + " " + profit
                + "  |  Pot $" + formatMoney(result.totalPot())
                + "  |  " + tableName
                + "  |  " + rank.replace("_", " ");
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatMoney(int amount) {
        return String.format("%,d", amount);
    }

    private String formatSignedMoney(int amount) {
        String prefix = amount > 0 ? "+$" : amount < 0 ? "-$" : "$";
        return prefix + formatMoney(Math.abs(amount));
    }
}
