import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradebookManager {
    static {
        EnvLoader.loadEnv(".env");
    }

    private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + EnvLoader.get("DB_NAME") + "?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = EnvLoader.get("DB_USER");
    private static final String PASSWORD = EnvLoader.get("DB_PASS");

    private static boolean exit = false;

    /**
     * Adds a new class to the course table
     * @param conn MySQL Database connection
     * @param course course number, e.g. CS410
     * @param term course term, e.g. Fa26
     * @param section course section, e.g. 1
     * @param desc course description, e.g. Databases
     * @throws SQLException Exception thrown if the SQL statement cannot be executed
     */
    private static void NewClass(Connection conn, String course, String term, String section, String desc) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO class (courseNum, term, sectionNum, description) " + 
            "VALUES (?, ?, ?, ?);"
        );

        pstmt.setString(1, course);
        pstmt.setString(2, term);
        pstmt.setString(3, section);
        pstmt.setString(4, desc);

        pstmt.execute();
        
        System.out.println(course + ", " + term + ", " + section + ", " + desc + " added to course list");
    }

    /**
     * Parses command and args from user-given input
     * @param line the string that was provided by the user
     * @return array of tokenized 
     */
    @SuppressWarnings("CollectionsToArray")
    public static String[] parseCommand(String line) {
        List<String> tokens = new ArrayList<>();

        // The first capture group captures words inside of double quotes
        // The second capture group captures words not inside quotes
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(line);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Words in quotes
                tokens.add(matcher.group(1));
            } else {
                // Words not in quotes
                tokens.add(matcher.group(2));
            }
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * Given a string array that is a tokenized full command, 
     * e.g. "new-class <course> <term> <section>",
     * executes that command and prints the relevant response
     * @param fullCommand string array representing a given command
     */
    public static ResultSet executeCommand(Connection conn, String[] fullCommand) throws SQLException {
        switch(fullCommand[0]) {
            case "new-class" -> {
                if (fullCommand.length != 5) {
                    System.out.println("Incorrect number of arguments for \"new-class\"");
                    printUsage("new-class");
                    break;
                }

                NewClass(conn, fullCommand[1], fullCommand[2], fullCommand[3], fullCommand[4]);
            }
            case "list-classes" -> {
                // TODO: Implement command
            }
            case "select-class" -> {
                // TODO: Implement command
            }
            case "show-class" -> {
                // TODO: Implement command
            }
            case "show-categories" -> {
                // TODO: Implement command
            }
            case "add-category" -> {
                // TODO: Implement command
            }
            case "show-assignments" -> {
                // TODO: Implement command
            }
            case "add-assignment" -> {
                // TODO: Implement command
            }
            case "add-student" -> {
                // TODO: Implement command
            }
            case "show-students" -> {
                // TODO: Implement command
            }
            case "grade" -> {
                // TODO: Implement command
            }
            case "student-grades" -> {
                // TODO: Implement command
            }
            case "gradebook" -> {
                // TODO: Implement command
            }
            case "help" -> {
                // TODO: Implement command
            }
            case "quit", "exit" -> {
                System.out.println("Exiting...");
                exit = true;
            }
            default -> {
                System.out.println("The command \"" + fullCommand[0] + "\" could not be recognized.");
            }
        }
        return null;
    }

    public static void printUsage(String command) {
        System.out.println("Correct usage:");
        switch(command) {
            case "new-class" -> {
                System.out.println("new-class <course> <term> <section> \"<description>\"");
            }
            case "list-classes" -> {
                System.out.println("list-classes");
            }
            case "select-class" -> {
                System.out.println("select-class <course> [<term>] [<section>]");
            }
            case "show-class" -> {
                System.out.println("show-class");
            }
            case "show-categories" -> {
                System.out.println("show-categories");
            }
            case "add-category" -> {
                System.out.println("add-category <name> <weight>");
            }
            case "show-assignments" -> {
                System.out.println("show-assignments");
            }
            case "add-assignment" -> {
                System.out.println("add-assignment <name> <category> <points> \"<description>\"");
            }
            case "add-student" -> {
                System.out.println("If adding a new student to the gradebook:\nadd-student <username> <student-id> <last> <first>");
                System.out.println("If adding an existing student to the selected class:\nadd-student <username>");
            }
            case "show-students" -> {
                System.out.println("show-students [<string>]");
            }
            case "grade" -> {
                System.out.println("grade <assignment> <username> <points>");
            }
            case "student-grades" -> {
                System.out.println("student-grades <username>");
            }
            case "gradebook" -> {
                System.out.println("gradebook");
            }
            case "help" -> {
                System.out.println("help [<command>]");
            }
            case "quit", "exit" -> {
                System.out.println("Exiting...");
                exit = true;
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Welcome to the Gradebook Manager!");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            Scanner lineScan = new Scanner(System.in)) {

            // Command Loop
            while(!exit) {
                System.out.println("\nEnter a command ('help' if you need help):");
                String line = lineScan.nextLine();
                String[] fullCommand = parseCommand(line);
                executeCommand(conn, fullCommand);
            }
                
        } catch (Exception e) {
            System.err.println(e.getClass() + " Connection failed: " + e.getMessage());
        }
    }
}
