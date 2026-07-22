package pokergame.client.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pokergame.domain.model.Card;

import java.util.ArrayList;
import java.util.List;

public class CardParser {
    // Reusable object mapper instance
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses a Jackson JsonNode array into a Type-Safe List of Card domain objects.
     * * @param cardsArray The JSON array node from the network payload
     * @return A list of instantiated Card objects (empty list if payload is invalid)
     */
    public static List<Card> parseCardsFromJson(JsonNode cardsArray) {
        List<Card> cards = new ArrayList<>();

        if (cardsArray == null || !cardsArray.isArray()) {
            return cards;
        }

        for (JsonNode cardNode : cardsArray) {
            try {
                // Expecting JSON format: {"value": 14, "suit": "Spades"}
                if (cardNode.isObject() && cardNode.has("value") && cardNode.has("suit")) {
                    int value = cardNode.get("value").asInt();
                    String suit = cardNode.get("suit").asText(); // e.g., "Spades", "Hearts"

                    // Invokes your exact constructor: Card(int value, String suit)
                    cards.add(new Card(value, suit));
                }
            } catch (Exception e) {
                System.err.println("CardParser Warning: Failed to parse node -> " + cardNode.toString());
            }
        }

        return cards;
    }

    public static String getCardImagePath(Card card) {
        String suitStr = card.getSuit().substring(0, 1).toUpperCase();
        String valStr = switch (card.getValue()) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(card.getValue());
        };
        return "/images/" + valStr + "-" + suitStr + ".png";
    }

    public static List<Card> parseCardsString(String holeCardsStr) {
        List<Card> cards = new ArrayList<>();
        if (holeCardsStr == null || holeCardsStr.isEmpty() || "HIDDEN".equalsIgnoreCase(holeCardsStr)) {
            return cards;
        }

        String[] cardTokens = holeCardsStr.split(",");
        for (String token : cardTokens) {
            token = token.trim();
            if (token.length() == 2) {
                Card card = createCardFromChars(token.charAt(0), token.charAt(1));
                if (card != null) cards.add(card);
            }
        }
        return cards;
    }

    public static Card createCardFromChars(char rankChar, char suitChar) {
        int value = switch (rankChar) {
            case '2' -> 2;   case '3' -> 3;   case '4' -> 4;
            case '5' -> 5;   case '6' -> 6;   case '7' -> 7;
            case '8' -> 8;   case '9' -> 9;
            case 'T' -> 10;  case 'J' -> 11;  case 'Q' -> 12;
            case 'K' -> 13;  case 'A' -> 14;
            default -> -1;
        };

        String suit = switch (suitChar) {
            case 'h' -> "Hearts";
            case 'd' -> "Diamonds";
            case 'c' -> "Clubs";
            case 's' -> "Spades";
            default -> null;
        };

        if (value == -1 || suit == null) return null;
        return new Card(value, suit);
    }

}
