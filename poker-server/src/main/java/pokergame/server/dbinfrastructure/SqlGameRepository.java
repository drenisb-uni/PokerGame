package pokergame.server.dbinfrastructure;

import pokergame.domain.dto.*;
import pokergame.server.domain.repository.IGameRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class SqlGameRepository implements IGameRepository {
    private static final String BOT_PASSWORD_HASH = "BOT_ACCOUNT";

    private final HikariDSProvider ds;

    public SqlGameRepository(HikariDSProvider ds) {
        this.ds = ds;
    }

    private Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    @Override
    public HandHistoryDTO findHandHistoryById(String id) {
        String sql = "SELECT * FROM hand_histories WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new HandHistoryDTO(
                            rs.getString("id"),
                            rs.getInt("table_id"),
                            rs.getTimestamp("started_at").toLocalDateTime(),
                            rs.getString("community_cards"),
                            rs.getInt("total_pot"),
                            rs.getString("winning_hand_rank")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load hand history " + id + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void saveHandHistory(HandHistoryDTO handHistory) {
        String sql = """
                INSERT INTO hand_histories (id, table_id, started_at, community_cards, total_pot, winning_hand_rank)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    table_id = VALUES(table_id),
                    community_cards = VALUES(community_cards),
                    total_pot = VALUES(total_pot),
                    winning_hand_rank = VALUES(winning_hand_rank)
                """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, handHistory.id());
            stmt.setInt(2, handHistory.tableId());
            stmt.setTimestamp(3, Timestamp.valueOf(handHistory.startedAt()));
            stmt.setString(4, handHistory.communityCards());
            stmt.setInt(5, handHistory.totalPot());
            stmt.setString(6, handHistory.winningHandRank());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save hand history " + handHistory.id() + ": " + e.getMessage());
        }
    }

    @Override
    public HandActionDTO findHandActionById(String id) {
        String sql = "SELECT * FROM hand_actions WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Integer.parseInt(id));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new HandActionDTO(
                            rs.getInt("id"),
                            rs.getString("hand_id"),
                            rs.getString("player_id"),
                            rs.getString("round_stage"),
                            rs.getInt("sequence_number"),
                            rs.getString("action_type"),
                            rs.getInt("amount")
                    );
                }
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Could not load hand action " + id + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void saveHandAction(HandActionDTO handAction) {
        String playerId = resolvePlayerId(handAction.playerId());
        if (playerId == null) {
            return;
        }

        String sql = """
                INSERT INTO hand_actions (hand_id, player_id, round_stage, sequence_number, action_type, amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, handAction.handId());
            stmt.setString(2, playerId);
            stmt.setString(3, handAction.roundStage());
            stmt.setInt(4, handAction.sequenceNumber());
            stmt.setString(5, handAction.actionType());
            stmt.setInt(6, handAction.amount());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save hand action for " + handAction.playerId() + ": " + e.getMessage());
        }
    }

    @Override
    public HandParticipantDTO findHandParticipantById(String id) {
        String sql = """
                SELECT hp.*, pp.username
                FROM hand_participants hp
                JOIN player_profiles pp ON pp.id = hp.player_id
                WHERE hp.hand_id = ?
                LIMIT 1
                """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new HandParticipantDTO(
                            rs.getString("hand_id"),
                            rs.getString("username"),
                            rs.getInt("seat_index"),
                            rs.getString("hole_cards"),
                            rs.getInt("start_chips"),
                            rs.getInt("end_chips"),
                            rs.getInt("net_profit"),
                            rs.getBoolean("is_winner")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load hand participant " + id + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void saveHandParticipant(HandParticipantDTO handParticipant) {
        String playerId = resolvePlayerId(handParticipant.playerUsername());
        if (playerId == null) {
            return;
        }

        String deleteSql = "DELETE FROM hand_participants WHERE hand_id = ? AND player_id = ?";
        String insertSql = """
                INSERT INTO hand_participants
                    (hand_id, player_id, seat_index, hole_cards, start_chips, end_chips, net_profit, is_winner)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection()) {
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, handParticipant.handId());
                deleteStmt.setString(2, playerId);
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, handParticipant.handId());
                insertStmt.setString(2, playerId);
                insertStmt.setInt(3, handParticipant.seatIndex());
                insertStmt.setString(4, handParticipant.holeCards());
                insertStmt.setInt(5, handParticipant.startChips());
                insertStmt.setInt(6, handParticipant.endChips());
                insertStmt.setInt(7, handParticipant.netProfit());
                insertStmt.setBoolean(8, handParticipant.isWinner());
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Could not save hand participant " + handParticipant.playerUsername() + ": " + e.getMessage());
        }
    }

    @Override
    public PlayerProfileDTO findPlayerProfileById(String id) {
        String sql = "SELECT * FROM player_profiles WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapProfile(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load player profile " + id + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void savePlayerProfile(PlayerProfileDTO playerProfile) {
        String sql = """
                INSERT INTO player_profiles (id, username, email, password_hash, total_bankroll)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    username = VALUES(username),
                    email = COALESCE(VALUES(email), email),
                    total_bankroll = VALUES(total_bankroll)
                """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerProfile.id());
            stmt.setString(2, playerProfile.username());
            stmt.setString(3, playerProfile.email());
            stmt.setString(4, safePasswordHash(playerProfile.passwordHash()));
            stmt.setInt(5, playerProfile.totalBankroll());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save player profile " + playerProfile.username() + ": " + e.getMessage());
        }
    }

    @Override
    public PokerTableDTO findPokerTableById(String id) {
        String sql = "SELECT * FROM poker_tables WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Integer.parseInt(id));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PokerTableDTO(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("hoster_id")
                    );
                }
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Could not load poker table " + id + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void savePokerTable(PokerTableDTO pokerTable) {
        String sql = """
                INSERT INTO poker_tables (id, name, hoster_id)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    hoster_id = VALUES(hoster_id)
                """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pokerTable.id());
            stmt.setString(2, pokerTable.name());
            stmt.setString(3, pokerTable.hosterId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save poker table " + pokerTable.name() + ": " + e.getMessage());
        }
    }

    @Override
    public int findOrCreatePokerTable(String name, String hosterId) {
        String selectSql = "SELECT id FROM poker_tables WHERE name = ? ORDER BY id LIMIT 1";
        String insertSql = "INSERT INTO poker_tables (name, hoster_id) VALUES (?, ?)";

        try (Connection conn = getConnection()) {
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, name);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, name);
                insertStmt.setString(2, hosterId);
                insertStmt.executeUpdate();

                try (ResultSet keys = insertStmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not create poker table " + name + ": " + e.getMessage());
        }
        return 0;
    }

    @Override
    public List<PlayerHandResultDTO> findRecentHandsForPlayer(String playerId, int limit) {
        String sql = """
                SELECT
                    h.id AS hand_id,
                    COALESCE(t.name, CONCAT('Table ', h.table_id)) AS table_name,
                    h.started_at,
                    h.total_pot,
                    h.winning_hand_rank,
                    hp.net_profit,
                    hp.is_winner
                FROM hand_participants hp
                JOIN hand_histories h ON h.id = hp.hand_id
                LEFT JOIN poker_tables t ON t.id = h.table_id
                LEFT JOIN player_profiles pp ON pp.id = hp.player_id OR pp.username = hp.player_id
                WHERE hp.player_id = ? OR pp.id = ? OR pp.username = ?
                ORDER BY h.started_at DESC
                LIMIT ?
                """;
        List<PlayerHandResultDTO> results = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId);
            stmt.setString(2, playerId);
            stmt.setString(3, playerId);
            stmt.setInt(4, Math.max(1, limit));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new PlayerHandResultDTO(
                            rs.getString("hand_id"),
                            rs.getString("table_name"),
                            rs.getTimestamp("started_at").toLocalDateTime(),
                            rs.getInt("total_pot"),
                            rs.getString("winning_hand_rank"),
                            rs.getInt("net_profit"),
                            rs.getBoolean("is_winner")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load recent hands for " + playerId + ": " + e.getMessage());
        }
        return results;
    }

    private String resolvePlayerId(String usernameOrId) {
        String selectSql = "SELECT id FROM player_profiles WHERE id = ? OR username = ? LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, usernameOrId);
            stmt.setString(2, usernameOrId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not resolve player " + usernameOrId + ": " + e.getMessage());
        }

        return null;
    }

    private PlayerProfileDTO mapProfile(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new PlayerProfileDTO(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getInt("total_bankroll"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }

    private String safePasswordHash(String passwordHash) {
        return passwordHash == null || passwordHash.isBlank() ? BOT_PASSWORD_HASH : passwordHash;
    }
}
