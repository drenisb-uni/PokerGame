package pokergame;


import pokergame.domain.dto.PlayerProfileDTO;

import java.util.Map;

public class GameContext {
    private static PlayerProfileDTO userProfile;
    private static String jwtToken;
    private static String currentTableId = null;
    private static Map<String, Object> lastTableSnapshot;


    public static void setPlayerProfile(PlayerProfileDTO userProfile) {
        if (userProfile == null) throw new NullPointerException("UserProfile can't be null");
        GameContext.userProfile = userProfile;
    }
    public static PlayerProfileDTO getPlayerProfile() {
        return userProfile;
    }

    public static String getJwtToken() { return jwtToken; }
    public static void setJwtToken(String token) { jwtToken = token; }

    public static String getCurrentTableId() {
        return currentTableId;
    }
    public static void setCurrentTableId(String tableId) {
        currentTableId = tableId;
    }
    public static void clearCurrentTable() {
        currentTableId = null;
    }

    public static void setLastTableSnapshot(Map<String, Object> snapshot) {
        lastTableSnapshot = snapshot;
    }
    public static Map<String, Object> getLastTableSnapshot() {
        return lastTableSnapshot;
    }
}
