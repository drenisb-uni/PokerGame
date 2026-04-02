package pokergame.dbinfrastructure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class HikariDSProvider {
    private final String url;
    private final String username;
    private final String password;

    public HikariDSProvider(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    };
}
