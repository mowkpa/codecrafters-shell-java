import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "cd", "jobs"));

    private static class Job {
        int id;
        long pid;
        String command;
        Process process;

        Job(int id, long pid, String command, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    private static final List<Job> backgroundJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            // Parse input into tokens, respecting quotes and backslash escaping
            List<String> tokens = parseTokens(input);
            if (tokens.isEmpty()) continue;
            boolean runInBackground = false;
            if (tokens.get(tokens.size() - 1).equals("&")) {
                runInBackground = true;
                tokens.remove(tokens.size() - 1);
            }
            if (tokens.isEmpty()) continue;

            // ── Extract redirection from token list ──────────
            File stdoutFile = null;
            File stderrFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;
            List<String> cleanTokens = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                String tok = tokens.get(i);
                if ((tok.equals(">>") || tok.equals("1>>")) && i + 1 < tokens.size()) {
                    stdoutFile = new File(tokens.get(i + 1));
                    appendStdout = true;
                    i++; // skip the filename token
                } else if ((tok.equals(">") || tok.equals("1>")) && i + 1 < tokens.size()) {
                    stdoutFile = new File(tokens.get(i + 1));
                    appendStdout = false;
                    i++; // skip the filename token
                } else if (tok.equals("2>>") && i + 1 < tokens.size()) {
                    stderrFile = new File(tokens.get(i + 1));
                    appendStderr = true;
                    i++; // skip the filename token
                } else if (tok.equals("2>") && i + 1 < tokens.size()) {
                    stderrFile = new File(tokens.get(i + 1));
                    appendStderr = false;
                    i++; // skip the filename token
                } else {
                    cleanTokens.add(tok);
                }
            }
            tokens = cleanTokens;
            if (tokens.isEmpty()) continue;

            if (stdoutFile != null) {
                if (stdoutFile.getParentFile() != null) stdoutFile.getParentFile().mkdirs();
                new java.io.FileOutputStream(stdoutFile, appendStdout).close();
            }
            if (stderrFile != null) {
                if (stderrFile.getParentFile() != null) stderrFile.getParentFile().mkdirs();
                new java.io.FileOutputStream(stderrFile, appendStderr).close();
            }
            // ─────────────────────────────────────────────────────────────────

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
                printWithRedirect(String.join(" ", cmdArgs), stdoutFile, appendStdout);

            } else if (command.equals("pwd")) {
                printWithRedirect(System.getProperty("user.dir"), stdoutFile, appendStdout);

            } else if (command.equals("cd")) {
                String target = cmdArgs.isEmpty() ? "~" : cmdArgs.get(0);
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
                    printErrWithRedirect("cd: " + target + ": No such file or directory", stderrFile, appendStderr);
                }

            } else if (command.equals("type")) {
                if (cmdArgs.isEmpty()) continue;
                String arg = cmdArgs.get(0);
                String msg;
                if (BUILTINS.contains(arg)) {
                    msg = arg + " is a shell builtin";
                } else {
                    String executablePath = findExecutable(arg);
                    msg = executablePath != null ? arg + " is " + executablePath : arg + ": not found";
                }
                printWithRedirect(msg, stdoutFile, appendStdout);

            } else if (command.equals("jobs")) {
                for (Job job : backgroundJobs) {
                    System.out.printf("[%d]+  %-24s%s\n", job.id, "Running", job.command);
                }

            } else {
                String executablePath = findExecutable(command);
                if (executablePath != null) {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(command);
                    cmd.addAll(cmdArgs);

                    ProcessBuilder pb = new ProcessBuilder(cmd)
                            .directory(new File(System.getProperty("user.dir")));

                    if (stdoutFile != null) {
                        if (stdoutFile.getParentFile() != null) stdoutFile.getParentFile().mkdirs();
                        if (appendStdout) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutFile));
                        } else {
                            pb.redirectOutput(stdoutFile);
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (stderrFile != null) {
                        if (stderrFile.getParentFile() != null) stderrFile.getParentFile().mkdirs();
                        if (appendStderr) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(stderrFile));
                        } else {
                            pb.redirectError(stderrFile);
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
                    
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                    Process process = pb.start();
                    if (runInBackground) {
                        int jobId = backgroundJobs.size() + 1;
                        backgroundJobs.add(new Job(jobId, process.pid(), input, process));
                        System.out.println("[" + jobId + "] " + process.pid());
                    } else {
                        process.waitFor();
                    }
                } else {
                    System.out.println(command + ": not found");
                }
            }
        }
    }

    /** Prints a line either to stdout or to a file, depending on redirection. */
    private static void printWithRedirect(String line, File redirectFile, boolean append) throws Exception {
        if (redirectFile == null) {
            System.out.println(line);
        } else {
            if (redirectFile.getParentFile() != null) redirectFile.getParentFile().mkdirs();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(redirectFile, append);
                 PrintStream ps = new PrintStream(fos)) {
                ps.println(line);
            }
        }
    }

    /** Prints a line either to stderr or to a file, depending on redirection. */
    private static void printErrWithRedirect(String line, File redirectFile, boolean append) throws Exception {
        if (redirectFile == null) {
            System.err.println(line);
        } else {
            if (redirectFile.getParentFile() != null) redirectFile.getParentFile().mkdirs();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(redirectFile, append);
                 PrintStream ps = new PrintStream(fos)) {
                ps.println(line);
            }
        }
    }

    /**
     * Parses a shell input line into a list of tokens.
     * Handles:
     *  - Single-quoted strings: ALL chars literal (no escaping at all)
     *  - Double-quoted strings: \ only escapes " \ $ ` and newline; all other \x kept literally
     *  - Unquoted: \ escapes the next char unconditionally
     *  - Unquoted whitespace: token delimiter (collapsed)
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
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                    hasToken = true;
                }

            } else if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                } else if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`' || next == '\n') {
                        i++;
                        current.append(next);
                    } else {
                        current.append(c); // backslash is literal for all other chars
                    }
                    hasToken = true;
                } else {
                    current.append(c);
                    hasToken = true;
                }

            } else {
                // Unquoted context
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        i++;
                        current.append(input.charAt(i));
                        hasToken = true;
                    }
                } else if (c == '\'') {
                    inSingleQuote = true;
                    hasToken = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                    hasToken = true;
                } else if (c == ' ' || c == '\t') {
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

        if (hasToken) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

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