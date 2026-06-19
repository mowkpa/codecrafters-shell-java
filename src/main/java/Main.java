import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
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
            } else {
                System.out.println(input + ": command not found");
            }
        }

        scanner.close();
    }
}