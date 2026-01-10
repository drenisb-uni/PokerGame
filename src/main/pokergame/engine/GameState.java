package main.pokergame.engine;

public enum GameState {
    WAITING_FOR_PLAYERS,
    PRE_FLOP_BETTING,
    FLOP_DEALING,
    FLOP_BETTING,
    TURN_DEALING,
    TURN_BETTING,
    RIVER_DEALING,
    RIVER_BETTING,
    SHOWDOWN,
    HAND_OVER
}
