import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EnvLoader {
    private static final Map<String, String> envMap = new HashMap<>();

    public static void loadEnv(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Split by the first '=' character
                int delimiterIdx = line.indexOf("=");
                if (delimiterIdx > 0) {
                    String key = line.substring(0, delimiterIdx).trim();
                    String value = line.substring(delimiterIdx + 1).trim();
                    
                    // Strip optional wrapping quotes (e.g., "value" or 'value')
                    if ((value.startsWith("\"") && value.endsWith("\"")) || 
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    envMap.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load .env file: " + e.getMessage());
        }
    }

    public static String get(String key) {
        return envMap.get(key);
    }
}