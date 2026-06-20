import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd"));

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();

            if (input.equals("exit 0")) {
                break;
            }

            String[] parts = input.split(" ", 2);
            String command = parts[0];

            if (command.equals("echo")) {
                if (parts.length > 1) {
                    System.out.println(parts[1]);
                } else {
                    System.out.println();
                }
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
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

        String[] paths = pathEnv.split(File.pathSeparator);

        for (String dir : paths) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}