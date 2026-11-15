import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;

public class EnvLoader {
    private static final Properties props = new Properties();

    public static void loadEnv(String filePath) {
        try (Reader reader = new FileReader(filePath)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Could not load " + filePath + ": " + e.getMessage());
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}
