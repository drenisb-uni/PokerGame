package pokergame;


import pokergame.domain.dto.PlayerProfileDTO;

public class GameContext {
    private static PlayerProfileDTO userProfile;
    private static String jwtToken;
    private static String currentTableId = null;


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
}
