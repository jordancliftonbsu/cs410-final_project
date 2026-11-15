import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final int mysqlerrorDuplicate = 1062;
    private static final int mysqlerrorRecordDoesNotExist = 1452;

    private static boolean exit = false;

    /** The active class as {courseNum, term, sectionNum}, or null if none is selected. */
    private static String[] selectedClass = null;

    /** Usage text for each command, in the order help lists them. */
    private static final Map<String, String> USAGE = new LinkedHashMap<>();
    static {
        USAGE.put("new-class", "new-class <course> <term> <section> \"<description>\"");
        USAGE.put("list-classes", "list-classes");
        USAGE.put("select-class", "select-class <course> [<term>] [<section>]");
        USAGE.put("show-class", "show-class");
        USAGE.put("show-categories", "show-categories");
        USAGE.put("add-category", "add-category <name> <weight>");
        USAGE.put("show-assignments", "show-assignments");
        USAGE.put("add-assignment", "add-assignment <name> <category> <points> \"<description>\"");
        USAGE.put("add-student", "add-student <username> <student-id> <last> <first>   (new student)\n"
                               + "add-student <username>                               (existing student)");
        USAGE.put("show-students", "show-students [<string>]");
        USAGE.put("grade", "grade <assignment> <username> <points>");
        USAGE.put("student-grades", "student-grades <username>");
        USAGE.put("gradebook", "gradebook");
        USAGE.put("help", "help [<command>]");
        USAGE.put("quit", "quit   (also: exit)");
    }

    // ------------------------------------------------------------------
    // Class management
    // ------------------------------------------------------------------

    /**
     * Adds a new class to the Class table.
     * @param conn MySQL Database connection
     * @param course course number, e.g. CS410
     * @param term course term, e.g. Fa26
     * @param section course section, e.g. 1
     * @param desc course description, e.g. Databases
     * @throws SQLException thrown if the SQL statement cannot be executed
     */
    private static void newClass(Connection conn, String course, String term, String section, String desc) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO Class (courseNum, term, sectionNum, description) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, course);
            pstmt.setString(2, term);
            pstmt.setString(3, section);
            pstmt.setString(4, desc);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == mysqlerrorDuplicate) {
                System.out.println("error: class \"" + course + " " + term + " " + section + "\" already exists");
                return;
            }
            throw e;
        }
        System.out.println("Added class " + course + " " + term + " " + section + " \"" + desc + "\"");
    }

    /**
     * Prints every class with its enrollment count, ordered by term (oldest first,
     * Wi < Sp < Su < Fa within a year) and then course number.
     * @param conn MySQL Database connection
     * @throws SQLException thrown if the query fails
     */
    private static void listClasses(Connection conn) throws SQLException {
        String sql = """
            SELECT c.courseNum, c.term, c.sectionNum, c.description, COUNT(e.studentId) AS enrolled
            FROM Class c
            LEFT JOIN Enrollments e
              ON e.courseNum = c.courseNum AND e.term = c.term AND e.sectionNum = c.sectionNum
            GROUP BY c.courseNum, c.term, c.sectionNum, c.description
            ORDER BY CAST(SUBSTRING(c.term, 3) AS UNSIGNED),
                     FIELD(LEFT(c.term, 2), 'Wi', 'Sp', 'Su', 'Fa'),
                     c.courseNum, c.sectionNum
            """;
        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                rows.add(new String[] {
                    rs.getString("courseNum"), rs.getString("term"), rs.getString("sectionNum"),
                    nullToEmpty(rs.getString("description")), rs.getString("enrolled")
                });
            }
        }
        if (rows.isEmpty()) {
            System.out.println("(no classes yet)");
            return;
        }
        printTable(new String[] {"Course", "Term", "Section", "Description", "Students"}, rows);
    }

    /**
     * Makes a class active. Matches on course, and optionally term and section.
     * With no term, only the most recent term that has the course is considered.
     * Fails (listing the candidates) if more than one class still matches.
     * @param conn MySQL Database connection
     * @param course course number (required)
     * @param term term, or null for "most recent"
     * @param section section, or null if not given
     * @throws SQLException thrown if the query fails
     */
    private static void selectClass(Connection conn, String course, String term, String section) throws SQLException {
        String sql = """
            SELECT courseNum, term, sectionNum, description
            FROM Class
            WHERE courseNum = ?
              AND (? IS NULL OR term = ?)
              AND (? IS NULL OR sectionNum = ?)
            ORDER BY CAST(SUBSTRING(term, 3) AS UNSIGNED) DESC,
                     FIELD(LEFT(term, 2), 'Wi', 'Sp', 'Su', 'Fa') DESC,
                     sectionNum
            """;
        List<String[]> matches = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, course);
            pstmt.setString(2, term);
            pstmt.setString(3, term);
            pstmt.setString(4, section);
            pstmt.setString(5, section);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    matches.add(new String[] {
                        rs.getString("courseNum"), rs.getString("term"), rs.getString("sectionNum"),
                        nullToEmpty(rs.getString("description"))
                    });
                }
            }
        }

        if (matches.isEmpty()) {
            System.out.println("error: no class matches the given course/term/section");
            return;
        }

        // Rows are newest-term-first; when no term was given, keep only the newest term.
        if (term == null) {
            String newestTerm = matches.get(0)[1];
            matches.removeIf(row -> !row[1].equals(newestTerm));
        }

        if (matches.size() > 1) {
            System.out.println("error: more than one class matches; be more specific. Candidates:");
            printTable(new String[] {"Course", "Term", "Section", "Description"}, matches);
            return;
        }

        String[] chosen = matches.get(0);
        selectedClass = new String[] {chosen[0], chosen[1], chosen[2]};
        System.out.println("Selected " + chosen[0] + " " + chosen[1] + " section " + chosen[2]
                + (chosen[3].isEmpty() ? "" : " (" + chosen[3] + ")"));
    }

    /**
     * Shows the active class and how many students are enrolled in it.
     * @param conn MySQL Database connection
     * @throws SQLException thrown if the query fails
     */
    private static void showClass(Connection conn) throws SQLException {
        if (!requireClass()) {
            return;
        }
        String sql = """
            SELECT c.description, COUNT(e.studentId) AS enrolled
            FROM Class c
            LEFT JOIN Enrollments e
              ON e.courseNum = c.courseNum AND e.term = c.term AND e.sectionNum = c.sectionNum
            WHERE c.courseNum = ? AND c.term = ? AND c.sectionNum = ?
            GROUP BY c.courseNum, c.term, c.sectionNum, c.description
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindClass(pstmt, 1);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Active class: " + selectedClass[0] + " " + selectedClass[1]
                            + " section " + selectedClass[2]);
                    System.out.println("Description:  " + nullToEmpty(rs.getString("description")));
                    System.out.println("Enrolled:     " + rs.getInt("enrolled") + " student(s)");
                } else {
                    // The class was deleted behind our back.
                    selectedClass = null;
                    System.out.println("error: the selected class no longer exists; no class is selected");
                }
            }
        }
    }

    /**
     * Adds a new category of assignment to the currently selected class
     * @param conn MySQL Database connection
     * @param name Name of the assignment category
     * @param weight Weight of assignment grades in this category
     * @throws SQLException thrown if the query fails
     */
    private static void addCategory(Connection conn, String name, double weight) throws SQLException {
        if (!requireClass()) {
            return;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO Categories (courseNum, term, sectionNum, name, weight) VALUES (?, ?, ?, ?, ?)")) {
            pstmt.setString(1, selectedClass[0]);
            pstmt.setString(2, selectedClass[1]);
            pstmt.setString(3, selectedClass[2]);
            pstmt.setString(4, name);
            pstmt.setDouble(5, weight);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == mysqlerrorDuplicate) {
                System.out.println("error: class \"" + selectedClass[0] + " " + selectedClass[1] + " " + 
                selectedClass[2] + "\" already has a category with name \"" + name + "\"");
                return;
            }
            throw e;
        }
        System.out.println("Added category \"" + name + "\" to class: \"" + selectedClass[0] + " " + selectedClass[1] + 
        " " + selectedClass[2] + "\"");
    }

    /**
     * Adds an assignment to the currently selected class
     * @param conn MySQL Database connection
     * @param name Name of the assignment
     * @param category Category of the assignment
     * @param points How many points this assignment is worth
     * @param desc Description of the assignment
     * @throws SQLException thrown if the query fails
     */
    private static void addAssignment(Connection conn, String name, String category, double points, String desc) throws SQLException {
        if (!requireClass()) {
            return;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO Assignment (courseNum, term, sectionNum, name, categoryName, description, pointVal) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, selectedClass[0]);
            pstmt.setString(2, selectedClass[1]);
            pstmt.setString(3, selectedClass[2]);
            pstmt.setString(4, name);
            pstmt.setString(5, category);
            pstmt.setString(6, desc);
            pstmt.setDouble(7, points);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == mysqlerrorDuplicate) {
                System.out.println("error: category \"" + category + "\" already has an assignment with name \"" + name + "\"");
                return;
            }
            throw e;
        }
        System.out.println("Added assignment \"" + name + "\" to category \"" + category + "\"");
    }

    /**
     * Adds a new student to the currently selected class. If the name of the student has changed from 
     * what is on record, then the student's name is also updated.
     * @param conn MySQL Database connection
     * @param username Username of the student
     * @param studentId Student ID of the student
     * @param last Last name of the student
     * @param first First name of the student
     * @throws SQLException thrown if the query fails
     */
    private static void addStudent(Connection conn, String username, int studentId, String last, String first) throws SQLException {
        if (!requireClass()) {
            return;
        }
        String sql = """
            SELECT lastName, firstName
            FROM Student
            WHERE username = ?
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) { // Student exists
                    String oldLast = rs.getString(1);
                    String oldFirst = rs.getString(2);
                    if (!oldLast.equals(last) || !oldFirst.equals(first)) { // Name has changed, update record
                        System.out.println("Student name has changed... updating record");
                        System.out.println("Previous (Last, First): \"" + oldLast + ", " + oldFirst + "\"");
                        updateStudentName(conn, username, last, first);
                    }
                } else { // Student doesn't exist, so create
                    newStudent(conn, username, studentId, last, first);
                }
            }
        }
        addStudent(conn, username);
    }

    /**
     * Adds an existing student to the curently selected class.
     * @param conn MySQL Databse connection
     * @param username Username of the student
     * @throws SQLException thrown if the query fails
     */
    private static void addStudent(Connection conn, String username) throws SQLException {
        if (!requireClass()) {
            return;
        }
        int studentId = fetchStudentId(conn, username);
        if (studentId == -1) {
            return;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO Enrollments (studentId, courseNum, term, sectionNum) VALUES (?, ?, ?, ?)")) {
            pstmt.setInt(1, studentId);
            pstmt.setString(2, selectedClass[0]);
            pstmt.setString(3, selectedClass[1]);
            pstmt.setString(4, selectedClass[2]);
            pstmt.executeUpdate();
            System.out.println("Student with username \"" + username + "\" added to class: \"" + selectedClass[0] + " " + selectedClass[1] + 
            " section " + selectedClass[2] + "\"");
        } catch (SQLException e) {
            if (e.getErrorCode() == mysqlerrorDuplicate) {
                System.out.println("error: student with username \"" + username + "\" already in class \"" + 
                selectedClass[0] + " " + selectedClass[1] + " " + selectedClass[2] + "\"");
                return;
            } else if (e.getErrorCode() == mysqlerrorRecordDoesNotExist) {
                System.out.println("error: student with username \"" + username + "\" does not exist");
                return;
            }
            throw e;
        }
    }

    /**
     * Creates a new student
     * @param conn MySQL Database connection
     * @param username Username of the new student
     * @param studentId Student ID of the new student
     * @param last Last name of the new student
     * @param first First name of the new student
     * @throws SQLException thrown if the query fails
     */
    private static void newStudent(Connection conn, String username, int studentId, String last, String first) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO Student (studentId, username, lastName, firstName) VALUES (?, ?, ?, ?)")) {
            pstmt.setInt(1, studentId);
            pstmt.setString(2, username);
            pstmt.setString(3, last);
            pstmt.setString(4, first);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == mysqlerrorDuplicate) {
                System.out.println("error: student with username \"" + username + "\" already exists");
                return;
            }
            throw e;
        }
    }

    /**
     * Updates a student's name
     * @param conn MySQL Database connection
     * @param username Username of the student
     * @param last Last name of the student
     * @param first First name of the student
     * @throws SQLException thrown if the query fails
     */
    private static void updateStudentName(Connection conn, String username, String last, String first) throws SQLException {
        String sql = """
            UPDATE Student
            SET lastname = ?, firstname = ?
            WHERE username = ?;
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, last);
            pstmt.setString(2, first);
            pstmt.setString(3, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == mysqlerrorRecordDoesNotExist) {
                System.out.println("error: student with username \"" + username + "\" does not exist");
                return;
            }
            throw e;
        }
        System.out.println("Updated (Last, First): \"" + last + ", " + first + "\"");
    }

    /**
     * Given a student's username, returns their student ID
     * @param conn MySQL Database connection
     * @param username The student's username
     * @return The student's Student ID
     * @throws SQLException thrown if the query fails
     */
    private static int fetchStudentId(Connection conn, String username) throws SQLException {
        int studentId = -1;
        String sql = """
            SELECT studentId
            FROM Student
            WHERE username = ?
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) { // Student exists, grab Id
                    studentId = rs.getInt(1);
                } else { // Student doesn't exist, return error message
                    System.out.println("error: student with username \"" + username + "\" does not exist");
                }
            }
        }
        return studentId;
    }

    // ------------------------------------------------------------------
    // Helpers: class scoping, table printing, argument checking
    // ------------------------------------------------------------------

    /**
     * Checks that a class is selected, printing an error if not.
     * @return true if a class is selected
     */
    private static boolean requireClass() {
        if (selectedClass == null) {
            System.out.println("error: no class selected (use select-class first)");
            return false;
        }
        return true;
    }

    /**
     * Binds the selected class's course, term and section to three consecutive parameters.
     * @param pstmt the statement to bind
     * @param startIndex index of the first of the three parameters
     * @throws SQLException thrown if binding fails
     */
    private static void bindClass(PreparedStatement pstmt, int startIndex) throws SQLException {
        pstmt.setString(startIndex, selectedClass[0]); // CourseNum
        pstmt.setString(startIndex + 1, selectedClass[1]); // Term
        pstmt.setString(startIndex + 2, selectedClass[2]); // Section
    }

    /**
     * Prints rows as an aligned, left-justified table with a header and rule.
     * @param headers column titles
     * @param rows the data; each row has one string per column
     */
    private static void printTable(String[] headers, List<String[]> rows) {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        StringBuilder fmt = new StringBuilder();
        StringBuilder rule = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            fmt.append(i == 0 ? "" : "  ").append("%-").append(widths[i]).append("s");
            rule.append(i == 0 ? "" : "  ").append("-".repeat(widths[i]));
        }
        fmt.append("%n");
        System.out.printf(fmt.toString(), (Object[]) headers);
        System.out.println(rule);
        for (String[] row : rows) {
            System.out.printf(fmt.toString(), (Object[]) row);
        }
    }

    /**
     * Returns the empty string for null, so nullable columns print cleanly.
     * @param s a possibly-null string
     * @return s, or "" if s was null
     */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Checks a command's argument count, printing an error and its usage if wrong.
     * @param command the command name
     * @param args the full tokenized command (args[0] is the command itself)
     * @param min minimum number of arguments after the command name
     * @param max maximum number of arguments after the command name
     * @return true if the count is acceptable
     */
    private static boolean checkArgs(String command, String[] args, int min, int max) {
        int given = args.length - 1;
        if (given < min || given > max) {
            System.out.println("error: wrong number of arguments for \"" + command + "\"");
            printUsage(command);
            return false;
        }
        return true;
    }

    /**
     * Prints a one-line error for the commands that exist in the spec but are not built yet.
     * @param command the command name
     */
    private static void notImplemented(String command) {
        System.out.println("error: \"" + command + "\" is not implemented yet");
    }

    // ------------------------------------------------------------------
    // Parsing and dispatch
    // ------------------------------------------------------------------

    /**
     * Splits a line into tokens, treating double-quoted text as a single token.
     * @param line the string that was provided by the user
     * @return array of tokens; empty if the line was blank
     */
    public static String[] parseCommand(String line) {
        List<String> tokens = new ArrayList<>();

        // The first capture group captures words inside of double quotes
        // The second capture group captures words not inside quotes
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(line);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                tokens.add(matcher.group(1));
            } else {
                tokens.add(matcher.group(2));
            }
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * Executes one tokenized command and prints the result. User mistakes are reported
     * with an "error:" line; only unexpected database failures are thrown to the caller.
     * @param conn MySQL Database connection
     * @param fullCommand the tokenized command, fullCommand[0] being the command name
     * @throws SQLException thrown if a database call fails unexpectedly
     */
    public static void executeCommand(Connection conn, String[] fullCommand) throws SQLException {
        String command = fullCommand[0];
        switch (command) {
            case "new-class" -> {
                if (checkArgs(command, fullCommand, 4, 4)) {
                    newClass(conn, fullCommand[1], fullCommand[2], fullCommand[3], fullCommand[4]);
                }
            }
            case "list-classes" -> {
                if (checkArgs(command, fullCommand, 0, 0)) {
                    listClasses(conn);
                }
            }
            case "select-class" -> {
                if (checkArgs(command, fullCommand, 1, 3)) {
                    String term = fullCommand.length > 2 ? fullCommand[2] : null;
                    String section = fullCommand.length > 3 ? fullCommand[3] : null;
                    selectClass(conn, fullCommand[1], term, section);
                }
            }
            case "show-class" -> {
                if (checkArgs(command, fullCommand, 0, 0)) {
                    showClass(conn);
                }
            }
            case "add-category" -> {
                if (checkArgs(command, fullCommand, 2, 2)) {
                    addCategory(conn, fullCommand[1], Double.parseDouble(fullCommand[2]));
                }
            }
            case "add-assignment" -> {
                if (checkArgs(command, fullCommand, 4, 4)) {
                    addAssignment(conn, fullCommand[1], fullCommand[2], Double.parseDouble(fullCommand[3]), fullCommand[4]);
                }
            }
            case "add-student" -> {
                if (fullCommand.length == 2) {
                    if (checkArgs(command, fullCommand, 1, 1)) {
                        addStudent(conn, fullCommand[1]);
                    }
                } else {
                    if (checkArgs(command, fullCommand, 4, 4)) {
                        addStudent(conn, fullCommand[1], Integer.parseInt(fullCommand[2]), fullCommand[3], fullCommand[4]);
                    }
                }
            }
            case "show-categories", "show-assignments", "show-students",
                 "grade", "student-grades", "gradebook" -> notImplemented(command);
            case "help" -> {
                if (checkArgs(command, fullCommand, 0, 1)) {
                    help(fullCommand.length > 1 ? fullCommand[1] : null);
                }
            }
            case "quit", "exit" -> {
                System.out.println("Exiting...");
                exit = true;
            }
            default -> System.out.println("error: unknown command \"" + command + "\" (type 'help')");
        }
    }

    /**
     * Prints usage for one command, or the whole command list if none is given.
     * @param command a command name, or null for all
     */
    private static void help(String command) {
        if (command == null) {
            System.out.println("Commands:");
            for (String usage : USAGE.values()) {
                System.out.println("  " + usage);
            }
        } else if (USAGE.containsKey(command)) {
            printUsage(command);
        } else {
            System.out.println("error: unknown command \"" + command + "\"");
        }
    }

    /**
     * Prints the usage line for a command.
     * @param command the command name
     */
    public static void printUsage(String command) {
        String usage = USAGE.get(command.equals("exit") ? "quit" : command);
        if (usage != null) {
            System.out.println("usage: " + usage);
        }
    }

    /**
     * Turns a database exception a user can cause into a one-line message.
     * @param e the exception
     * @return text for an "error:" line
     */
    private static String describe(SQLException e) {
        return switch (e.getErrorCode()) {
            case mysqlerrorDuplicate -> "that already exists";
            case 3819 -> "a value violates a constraint (check the numbers you gave)";
            case mysqlerrorRecordDoesNotExist -> "a referenced record does not exist";
            case 1406 -> "a value is too long";
            default -> e.getMessage();
        };
    }

    /**
     * Prompt shown before each command; includes the active class if there is one.
     * @return the prompt string
     */
    private static String prompt() {
        if (selectedClass == null) {
            return "gradebook> ";
        }
        return "gradebook [" + selectedClass[0] + " " + selectedClass[1] + " " + selectedClass[2] + "]> ";
    }

    /**
     * Starts the shell: connects, then reads and runs one command per line until
     * quit, exit, or end-of-input (Ctrl-D).
     * @param args unused
     */
    public static void main(String[] args) {
        System.out.println("Welcome to the Gradebook Manager! Type 'help' for commands.");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Scanner lineScan = new Scanner(System.in)) {

            while (!exit) {
                System.out.print(prompt());
                System.out.flush();
                if (!lineScan.hasNextLine()) {
                    System.out.println();
                    break;
                }
                String[] fullCommand = parseCommand(lineScan.nextLine());
                if (fullCommand.length == 0) {
                    continue;
                }
                try {
                    executeCommand(conn, fullCommand);
                } catch (SQLException e) {
                    System.out.println("error: " + describe(e));
                } catch (RuntimeException e) {
                    System.out.println("error: unexpected problem running that command (" + e.getMessage() + ")");
                }
            }
        } catch (SQLException e) {
            System.out.println("error: could not connect to the database: " + e.getMessage());
            System.exit(1);
        }
    }
}
