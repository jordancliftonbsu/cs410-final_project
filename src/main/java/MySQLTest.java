import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class MySQLTest {
    static {
        EnvLoader.loadEnv("config.properties");
    }

    private static final String URL = EnvLoader.get("DB_URL");
    private static final String USER = EnvLoader.get("DB_USER");
    private static final String PASSWORD = EnvLoader.get("DB_PASS");

    @SuppressWarnings("CallToPrintStackTrace")
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT VERSION()");

            if (rs.next()) {
                System.out.println("Version: " + rs.getString(1));
            }
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}