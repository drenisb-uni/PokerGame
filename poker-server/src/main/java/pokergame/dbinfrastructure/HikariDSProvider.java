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
        this.url = dbUrl;//"jdbc:mysql://localhost:3306/poker_db";
        this.username = "root";
        this.password = "11X.gjiaDB";
    }

    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    };
}
