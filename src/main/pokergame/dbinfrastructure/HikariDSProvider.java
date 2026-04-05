package pokergame.dbinfrastructure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class HikariDSProvider {
    private final String url;
    private final String username;
    private final String password;


    private final String dbUrl = "jdbc:mysql://localhost:3306/poker_db";
    private final String dbUser = "root";
    private final String dbPass = "11X.gjiaDB";

    public HikariDSProvider() {
        this.url = dbUrl;
        this.username = dbUser;
        this.password = dbPass;
    }

    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    };
}
