import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd"));

    public static void main(String[] args) {
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
            } else if (command.equals("type")) {
                if (parts.length < 2) {
                    continue;
                }

                String arg = parts[1];

                if (BUILTINS.contains(arg)) {
                    System.out.println(arg + " is a shell builtin");
                } else {
                    System.out.println(arg + ": not found");
                }
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else {
                System.out.println(command + ": not found");
            }
        }
    }
}