package pokergame.engine;

import pokergame.domain.model.TableSeat;

public interface IPublicActionAPI {
    void Fold(TableSeat actor);

    void Call(TableSeat actor);

    void Raise(TableSeat actor, int amount);
}
