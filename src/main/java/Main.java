import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
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

            String[] parts = input.split(" ", 2);
            String command = parts[0];

            if (command.equals("exit")) {
                int exitCode = 0;
                if (parts.length > 1) {
                    try {
                        exitCode = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignored) {}
                }
                System.exit(exitCode);
            } else if (command.equals("echo")) {
                if (parts.length > 1) {
                    System.out.println(parts[1]);
                } else {
                    System.out.println();
                }
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (command.equals("cd")) {
                String target = (parts.length < 2 || parts[1].trim().isEmpty())
                        ? "~"
                        : parts[1].trim();
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
                    System.err.println("cd: " + parts[1].trim() + ": No such file or directory");
                }
            } else if (command.equals("type")) {
                if (parts.length < 2) {
                    continue;
                }

                String arg = parts[1];

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
                    String[] tokens = input.split(" ");

                    Process process = new ProcessBuilder(tokens)
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