package pokergame.engine;

public interface IPublicActionAPI {
    void Fold(String actorUsername);

    void Call(String actorUsername);

    void Raise(String actorUsername, int amount);
}
