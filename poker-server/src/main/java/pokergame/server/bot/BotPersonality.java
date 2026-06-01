package pokergame.server.bot;

public enum BotPersonality {
    BALANCED("Balanced", 0, 0, 72, 0.55, 45, 0.22, 1.00, 1.00, 1.00),
    TIGHT("Tight", -7, -8, 78, 0.45, 55, 0.12, 0.55, 0.80, 0.65),
    LOOSE("Loose", 4, 13, 70, 0.48, 42, 0.20, 1.10, 0.90, 1.55),
    AGGRESSIVE("Aggro", 2, 3, 65, 0.72, 38, 0.34, 1.35, 1.30, 1.10),
    TRICKY("Tricky", 0, 5, 70, 0.58, 35, 0.40, 1.80, 1.10, 1.25);

    public final String prefix;
    public final int strengthBias;
    public final int callBonus;
    public final int valueRaiseThreshold;
    public final double valueRaiseChance;
    public final int semiBluffThreshold;
    public final double semiBluffChance;
    public final double bluffScale;
    public final double raiseScale;
    public final double stubbornScale;

    BotPersonality(String prefix, int strengthBias, int callBonus, int valueRaiseThreshold,
                   double valueRaiseChance, int semiBluffThreshold, double semiBluffChance,
                   double bluffScale, double raiseScale, double stubbornScale) {
        this.prefix = prefix;
        this.strengthBias = strengthBias;
        this.callBonus = callBonus;
        this.valueRaiseThreshold = valueRaiseThreshold;
        this.valueRaiseChance = valueRaiseChance;
        this.semiBluffThreshold = semiBluffThreshold;
        this.semiBluffChance = semiBluffChance;
        this.bluffScale = bluffScale;
        this.raiseScale = raiseScale;
        this.stubbornScale = stubbornScale;
    }
}