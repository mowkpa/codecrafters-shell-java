import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("echo", "exit", "type"));

    private static File findExecutable(String command) {
        String path = System.getenv("PATH");
        if (path == null) return null;

        for (String dir : path.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();

            if (input.trim().isEmpty()) {
                continue;
            }

            String[] tokens = input.trim().split("\\s+");
            String command = tokens[0];

            if ("exit".equals(command)) {
                if (tokens.length > 1 && "0".equals(tokens[1])) {
                    break;
                }
                break;
            }

            if ("echo".equals(command)) {
                for (int i = 1; i < tokens.length; i++) {
                    if (i > 1) System.out.print(" ");
                    System.out.print(tokens[i]);
                }
                System.out.println();
                continue;
            }

            if ("type".equals(command)) {
                if (tokens.length < 2) {
                    continue;
                }

                String target = tokens[1];

                if (BUILTINS.contains(target)) {
                    System.out.println(target + " is a shell builtin");
                } else {
                    File executable = findExecutable(target);

                    if (executable != null) {
                        System.out.println(target + " is " + executable.getAbsolutePath());
                    } else {
                        System.out.println(target + ": not found");
                    }
                }
                continue;
            }

            File executable = findExecutable(command);

            if (executable != null) {
                List<String> cmd = new ArrayList<>();
                cmd.add(executable.getAbsolutePath());

                for (int i = 1; i < tokens.length; i++) {
                    cmd.add(tokens[i]);
                }

                Process process = new ProcessBuilder(cmd).start();

                BufferedReader out =
                        new BufferedReader(
                                new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = out.readLine()) != null) {
                    System.out.println(line);
                }

                BufferedReader err =
                        new BufferedReader(
                                new InputStreamReader(process.getErrorStream()));

                while ((line = err.readLine()) != null) {
                    System.out.println(line);
                }

                process.waitFor();
            } else {
                System.out.println(command + ": command not found");
            }
        }

        scanner.close();
    }
}