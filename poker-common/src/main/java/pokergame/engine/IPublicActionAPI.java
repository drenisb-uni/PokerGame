package pokergame.engine;

public interface IPublicActionAPI {
    void Fold(TableSeat actor);

    void Call(TableSeat actor);

    void Raise(TableSeat actor, int amount);
}
