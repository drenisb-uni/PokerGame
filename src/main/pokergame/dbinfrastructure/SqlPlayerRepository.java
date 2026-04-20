package pokergame.dbinfrastructure;

import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.repository.IPlayerRepository;

import java.sql.*;

public class SqlPlayerRepository implements IPlayerRepository {

    private final HikariDSProvider ds;

    public SqlPlayerRepository(HikariDSProvider ds) {
        this.ds = ds;
    }

    private Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    @Override
    public PlayerProfileDTO findProfileById(String id) {
        String query = "SELECT * FROM player_profiles WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, id);

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
    public PlayerProfileDTO findProfileByUsername(String username) {
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
    public void saveProfile(PlayerProfileDTO profile) {
        String sql = "INSERT INTO player_profiles (id, username, email, password_hash, total_bankroll) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "email = VALUES(email), " +
                "password_hash = VALUES(password_hash), " +
                "total_bankroll = VALUES(total_bankroll)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, profile.id());
            stmt.setString(2, profile.username());
            stmt.setString(3, profile.email());
            stmt.setString(4, profile.passwordHash());
            stmt.setInt(5, profile.totalBankroll());

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private PlayerProfileDTO mapRowToProfile(ResultSet rs) throws SQLException {
        return new PlayerProfileDTO(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getInt("total_bankroll"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}