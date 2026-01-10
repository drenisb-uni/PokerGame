package main.pokergame.domain.rules;
import java.util.List;

public class HandResult implements Comparable<HandResult> {
    private final HandType type;
    private final List<Integer> kickers;

    public HandResult(HandType type, List<Integer> kickers) {
        this.type = type;
        this.kickers = kickers;
    }

    public HandType getType() { return type; }

    @Override
    public int compareTo(HandResult o) {
        int typeCompare = Integer.compare(this.type.getValue(), o.type.getValue());
        if (typeCompare != 0) return typeCompare;

        for (int i = 0; i < this.kickers.size(); i++) {
            int compare = Integer.compare(this.kickers.get(i), o.kickers.get(i));
            if (compare != 0) return compare;
        }
        return 0;
    }
}
