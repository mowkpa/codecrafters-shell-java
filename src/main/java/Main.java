import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();

            if (input.equals("exit")) {
                break;
            }

            if (input.startsWith("echo")) {
                if (input.length() > 4) {
                    System.out.println(input.substring(5));
                } else {
                    System.out.println();
                }
                continue;
            }

            if (input.startsWith("type ")) {
                String command = input.substring(5);

                if (command.equals("echo") || command.equals("exit") || command.equals("type")) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    String[] paths = pathEnv.split(File.pathSeparator);
                    boolean found = false;

                    for (String path : paths) {
                        File file = new File(path, command);

                        if (file.exists() && file.canExecute()) {
                            System.out.println(command + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        System.out.println(command + ": not found");
                    }
                }
                continue;
            }

            String[] parts = input.split("\\s+");
            String command = parts[0];

            String pathEnv = System.getenv("PATH");
            String[] paths = pathEnv.split(File.pathSeparator);

            File executable = null;

            for (String path : paths) {
                File file = new File(path, command);

                if (file.exists() && file.canExecute()) {
                    executable = file;
                    break;
                }
            }

            if (executable != null) {
                List<String> cmd = new ArrayList<>();
                cmd.add(executable.getAbsolutePath());

                for (int i = 1; i < parts.length; i++) {
                    cmd.add(parts[i]);
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                Process process = pb.start();

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
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