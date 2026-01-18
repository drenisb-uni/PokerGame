package main.pokergame.domain.model;

public class PlayerProfile {
    private final String id;
    private final String username;
    private int totalBankroll;

    public PlayerProfile(String id, String username, int totalBankroll) {
        this.id = id;
        this.username = username;
        this.totalBankroll = totalBankroll;
    }

    public String getId() {return id;}
    public String getUsername() {return username;}
    public int getTotalBankroll() {return totalBankroll;}

    public void setTotalBankroll(int totalBankroll) {this.totalBankroll = totalBankroll;}
}
