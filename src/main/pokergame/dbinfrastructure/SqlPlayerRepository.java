package main.pokergame.dbinfrastructure;

import main.pokergame.domain.model.PlayerProfile;
import main.pokergame.domain.repository.PlayerRepository;

import java.sql.*;

public class SqlPlayerRepository implements PlayerRepository {

    private final DataSource ds;

    public SqlPlayerRepository(DataSource ds) {
        this.ds = ds;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    };

    @Override
    public PlayerProfile findProfileById(String id) {
        String query = "SELECT * FROM player_profiles WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToProfile(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public PlayerProfile findProfileByUsername(String username) {
        String query = "SELECT * FROM player_profiles WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToProfile(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        String sql = "INSERT INTO player_profiles (id, username, total_bankroll) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE total_bankroll = VALUES(total_bankroll)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, profile.getId());
            stmt.setString(2, profile.getUsername());
            stmt.setInt(3, profile.getTotalBankroll());

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private PlayerProfile mapRowToProfile(ResultSet rs) throws SQLException {
        return new PlayerProfile(
                rs.getString("id"),
                rs.getString("username"),
                rs.getInt("total_bankroll")
        );
    }
}
