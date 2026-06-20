import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "cd"));

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            // Parse input into tokens, respecting single quotes
            List<String> tokens = parseTokens(input);
            if (tokens.isEmpty()) continue;

            String command = tokens.get(0);
            List<String> cmdArgs = tokens.subList(1, tokens.size());

            if (command.equals("exit")) {
                int exitCode = 0;
                if (!cmdArgs.isEmpty()) {
                    try {
                        exitCode = Integer.parseInt(cmdArgs.get(0).trim());
                    } catch (NumberFormatException ignored) {}
                }
                System.exit(exitCode);

            } else if (command.equals("echo")) {
                System.out.println(String.join(" ", cmdArgs));

            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));

            } else if (command.equals("cd")) {
                String target = cmdArgs.isEmpty() ? "~" : cmdArgs.get(0);
                // Expand ~ to the HOME environment variable
                if (target.equals("~") || target.startsWith("~/")) {
                    String home = System.getenv("HOME");
                    if (home == null) home = System.getProperty("user.home");
                    target = home + target.substring(1);
                }
                File dir = new File(target).isAbsolute()
                        ? new File(target)
                        : new File(System.getProperty("user.dir"), target);
                if (dir.exists() && dir.isDirectory()) {
                    System.setProperty("user.dir", dir.getCanonicalPath());
                } else {
                    System.err.println("cd: " + target + ": No such file or directory");
                }

            } else if (command.equals("type")) {
                if (cmdArgs.isEmpty()) continue;
                String arg = cmdArgs.get(0);
                if (BUILTINS.contains(arg)) {
                    System.out.println(arg + " is a shell builtin");
                } else {
                    String executablePath = findExecutable(arg);
                    if (executablePath != null) {
                        System.out.println(arg + " is " + executablePath);
                    } else {
                        System.out.println(arg + ": not found");
                    }
                }

            } else {
                String executablePath = findExecutable(command);
                if (executablePath != null) {
                    // Build the command list: command + args (already parsed/unquoted)
                    List<String> cmd = new ArrayList<>();
                    cmd.add(command);
                    cmd.addAll(cmdArgs);

                    Process process = new ProcessBuilder(cmd)
                            .inheritIO()
                            .directory(new File(System.getProperty("user.dir")))
                            .start();

                    process.waitFor();
                } else {
                    System.out.println(command + ": not found");
                }
            }
        }
    }

    /**
     * Parses a shell input line into a list of tokens.
     * Handles:
     *  - Single-quoted strings: all chars literal, spaces preserved
     *  - Double-quoted strings: all chars literal (spaces preserved); $ and \ special in later stages
     *  - Unquoted whitespace: used as delimiter (collapsed)
     *  - Adjacent quoted/unquoted segments: concatenated into one token
     */
    private static List<String> parseTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean hasToken = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuote) {
                // Inside single quotes: everything is literal, no escaping
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                    hasToken = true;
                }
            } else if (inDoubleQuote) {
                // Inside double quotes: everything literal for now ($ and \ in later stages)
                if (c == '"') {
                    inDoubleQuote = false;
                } else {
                    current.append(c);
                    hasToken = true;
                }
            } else {
                // Unquoted context
                if (c == '\\') {
                    // Backslash: consume next character literally (strip the backslash)
                    if (i + 1 < input.length()) {
                        i++;
                        current.append(input.charAt(i));
                        hasToken = true;
                    }
                    // Trailing backslash at end of line is ignored
                } else if (c == '\'') {
                    inSingleQuote = true;
                    hasToken = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                    hasToken = true;
                } else if (c == ' ' || c == '\t') {
                    // Unquoted whitespace = token delimiter
                    if (hasToken) {
                        tokens.add(current.toString());
                        current.setLength(0);
                        hasToken = false;
                    }
                } else {
                    current.append(c);
                    hasToken = true;
                }
            }
        }

        // Add last token if present
        if (hasToken) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] paths = pathEnv.split(":");

        for (String dir : paths) {
            File file = new File(dir, command);

            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}