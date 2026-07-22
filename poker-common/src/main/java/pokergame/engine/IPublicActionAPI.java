package pokergame.engine;

public interface IPublicActionAPI {
    void Fold(String actorUsername);

    void Call(String actorUsername);

    void Raise(String actorUsername, int amount);
    // --- Table Management Lifecycles ---
    void JoinTable(String playerId, int buyIn);
    void LeaveTable(String playerId);
    void DisconnectPlayer(String playerId);

    // --- System Automation Actions ---
    void AddBot();
    void RefreshSnapshot(String playerId);
    void StartHand();
}
