package pokergame.server.bot;

public class OpponentProfile {
    private int folds;
    private int calls;
    private int raises;
    private int actions;

    public void record(String actionType) {
        actions++;
        if ("FOLD".equalsIgnoreCase(actionType) || "LEFT TABLE".equalsIgnoreCase(actionType)) {
            folds++;
        } else if ("RAISE".equalsIgnoreCase(actionType)) {
            raises++;
        } else if ("CALL".equalsIgnoreCase(actionType)) {
            calls++;
        }
    }

    public void absorb(OpponentProfile other) {
        this.folds += other.folds;
        this.calls += other.calls;
        this.raises += other.raises;
        this.actions += other.actions;
    }

    public double foldRate() {
        return actions == 0 ? 0.33 : (double) folds / actions;
    }

    public double callRate() {
        return actions == 0 ? 0.34 : (double) calls / actions;
    }

    public double raiseRate() {
        return actions == 0 ? 0.20 : (double) raises / actions;
    }
}