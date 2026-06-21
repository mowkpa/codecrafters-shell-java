import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    static class Job {
        int id;
        long pid;
        String command;
        String status;
        Process process;

        public Job(int id, long pid, String command, String status, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }

    static List<Job> backgroundJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            checkAndReapJobs(false);

            System.out.print("$ ");
            System.out.flush();

            String input = scanner.nextLine();
            if (input.isEmpty())
                continue;

            List<String> partsList = parseArguments(input);
            if (partsList.isEmpty())
                continue;

            String stdoutRedirectFile = null;
            String stderrRedirectFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;

            List<String> cleanParts = new ArrayList<>();
            for (int i = 0; i < partsList.size(); i++) {
                String part = partsList.get(i).trim();

                if (part.equals(">") || part.equals("1>")) {
                    appendStdout = false;
                    if (i + 1 < partsList.size())
                        stdoutRedirectFile = partsList.get(i + 1).trim();
                    i++;
                } else if (part.equals(">>") || part.equals("1>>")) {
                    appendStdout = true;
                    if (i + 1 < partsList.size())
                        stdoutRedirectFile = partsList.get(i + 1).trim();
                    i++;
                } else if (part.equals("2>")) {
                    appendStderr = false;
                    if (i + 1 < partsList.size())
                        stderrRedirectFile = partsList.get(i + 1).trim();
                    i++;
                } else if (part.equals("2>>")) {
                    appendStderr = true;
                    if (i + 1 < partsList.size())
                        stderrRedirectFile = partsList.get(i + 1).trim();
                    i++;
                } else {
                    cleanParts.add(part);
                }
            }
            partsList = cleanParts;

            boolean runInBackground = false;
            if (!partsList.isEmpty() && partsList.get(partsList.size() - 1).equals("&")) {
                runInBackground = true;
                partsList.remove(partsList.size() - 1);
            }

            if (stdoutRedirectFile != null) {
                File f = new File(stdoutRedirectFile);
                if (f.getParentFile() != null)
                    f.getParentFile().mkdirs();
                if (!appendStdout) {
                    Files.writeString(f.toPath(), "");
                } else if (!f.exists()) {
                    Files.writeString(f.toPath(), "");
                }
            }
            if (stderrRedirectFile != null) {
                File f = new File(stderrRedirectFile);
                if (f.getParentFile() != null)
                    f.getParentFile().mkdirs();
                if (!appendStderr) {
                    Files.writeString(f.toPath(), "");
                } else if (!f.exists()) {
                    Files.writeString(f.toPath(), "");
                }
            }

            List<List<String>> pipelineCommands = new ArrayList<>();
            List<String> currentCmd = new ArrayList<>();
            for (String part : partsList) {
                if (part.equals("|")) {
                    pipelineCommands.add(currentCmd);
                    currentCmd = new ArrayList<>();
                } else {
                    currentCmd.add(part);
                }
            }
            pipelineCommands.add(currentCmd);

            // --- UPGRADED: Mixed Built-in & External Pipeline Logic ---
            if (pipelineCommands.size() > 1) {
                String pendingBuiltinOutput = null;

                for (int i = 0; i < pipelineCommands.size();) {
                    List<String> cmdArgs = pipelineCommands.get(i);
                    String cmd = cmdArgs.get(0);

                    if (isBuiltinCommand(cmd)) {
                        String outputStr = executeBuiltinForPipeline(cmdArgs);
                        if (outputStr != null) {
                            if (i == pipelineCommands.size() - 1) {
                                printOrRedirect(outputStr, stdoutRedirectFile, appendStdout, stderrRedirectFile,
                                        appendStderr, 1);
                            } else {
                                pendingBuiltinOutput = outputStr + "\n";
                            }
                        } else {
                            pendingBuiltinOutput = null;
                        }
                        i++;
                    } else {
                        // Group contiguous external commands
                        List<List<String>> externalGroup = new ArrayList<>();
                        int j = i;
                        while (j < pipelineCommands.size() && !isBuiltinCommand(pipelineCommands.get(j).get(0))) {
                            externalGroup.add(pipelineCommands.get(j));
                            j++;
                        }

                        List<ProcessBuilder> pbs = new ArrayList<>();
                        for (int k = 0; k < externalGroup.size(); k++) {
                            ProcessBuilder pb = new ProcessBuilder(externalGroup.get(k));
                            pb.directory(new File(System.getProperty("user.dir")));
                            pbs.add(pb);
                        }

                        // Input mapping
                        if (pendingBuiltinOutput != null) {
                            pbs.get(0).redirectInput(ProcessBuilder.Redirect.PIPE);
                        } else if (i == 0) {
                            pbs.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);
                        }

                        // Output mapping
                        boolean isLastGroup = (j == pipelineCommands.size());
                        ProcessBuilder lastPb = pbs.get(pbs.size() - 1);

                        if (isLastGroup) {
                            if (stdoutRedirectFile != null) {
                                File f = new File(stdoutRedirectFile);
                                if (appendStdout)
                                    lastPb.redirectOutput(ProcessBuilder.Redirect.appendTo(f));
                                else
                                    lastPb.redirectOutput(f);
                            } else {
                                lastPb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }

                            if (stderrRedirectFile != null) {
                                File f = new File(stderrRedirectFile);
                                if (appendStderr)
                                    lastPb.redirectError(ProcessBuilder.Redirect.appendTo(f));
                                else
                                    lastPb.redirectError(f);
                            } else {
                                lastPb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            }
                        } else {
                            // If the next command is a builtin, external output is technically discarded
                            lastPb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                        }

                        for (int k = 0; k < pbs.size(); k++) {
                            if (!(isLastGroup && k == pbs.size() - 1)) {
                                pbs.get(k).redirectError(ProcessBuilder.Redirect.INHERIT);
                            }
                        }

                        try {
                            List<Process> processes = ProcessBuilder.startPipeline(pbs);

                            // Push captured Java strings directly into the external OS pipe
                            if (pendingBuiltinOutput != null) {
                                Process firstProcess = processes.get(0);
                                java.io.OutputStream os = firstProcess.getOutputStream();
                                os.write(pendingBuiltinOutput.getBytes());
                                os.flush();
                                os.close(); // Signal End of File to the process
                                pendingBuiltinOutput = null;
                            }

                            if (runInBackground && isLastGroup) {
                                int jobId = 1;
                                for (Job job : backgroundJobs) {
                                    if (job.id >= jobId)
                                        jobId = job.id + 1;
                                }
                                Process lastProcess = processes.get(processes.size() - 1);
                                System.out.println("[" + jobId + "] " + lastProcess.pid());

                                StringBuilder cmdBuilder = new StringBuilder();
                                for (int k = 0; k < partsList.size(); k++) {
                                    cmdBuilder.append(partsList.get(k));
                                    if (k < partsList.size() - 1)
                                        cmdBuilder.append(" ");
                                }

                                backgroundJobs.add(new Job(jobId, lastProcess.pid(), cmdBuilder.toString(), "Running",
                                        lastProcess));
                            } else {
                                for (Process p : processes) {
                                    p.waitFor();
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Error executing pipeline: " + e.getMessage());
                        }

                        i = j;
                    }
                }
                continue;
            }
            // -----------------------------------------------------------

            String[] parts = partsList.toArray(new String[0]);
            if (parts.length == 0)
                continue;
            String command = parts[0];

            if (command.equals("exit")) {
                break;
            } else if (command.equals("jobs")) {
                checkAndReapJobs(true);
                continue;
            } else if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    sb.append(parts[i]);
                    if (i < parts.length - 1)
                        sb.append(" ");
                }
                printOrRedirect(sb.toString(), stdoutRedirectFile, appendStdout, stderrRedirectFile, appendStderr, 1);

            } else if (command.equals("pwd")) {
                printOrRedirect(System.getProperty("user.dir"), stdoutRedirectFile, appendStdout, stderrRedirectFile,
                        appendStderr, 1);

            } else if (command.equals("cd")) {
                if (parts.length > 1) {
                    String targetPathStr = parts[1];

                    if (targetPathStr.startsWith("~")) {
                        String homeDir = System.getenv("HOME");
                        if (homeDir == null)
                            homeDir = System.getProperty("user.home");
                        targetPathStr = targetPathStr.replaceFirst("^~", homeDir);
                    }

                    Path currentDir = Path.of(System.getProperty("user.dir"));
                    Path targetPath = currentDir.resolve(targetPathStr).normalize();

                    if (Files.isDirectory(targetPath)) {
                        System.setProperty("user.dir", targetPath.toString());
                    } else {
                        printOrRedirect("cd: " + targetPathStr + ": No such file or directory", stdoutRedirectFile,
                                appendStdout, stderrRedirectFile, appendStderr, 2);
                    }
                }
            } else if (command.equals("type")) {
                if (parts.length > 1) {
                    String target = parts[1];
                    String outStr = null;
                    int targetStream = 1;

                    if (isBuiltinCommand(target)) {
                        outStr = target + " is a shell builtin";
                    } else {
                        String pathEnv = System.getenv("PATH");
                        boolean found = false;

                        if (pathEnv != null) {
                            String[] directories = pathEnv.split(File.pathSeparator);
                            for (String dir : directories) {
                                Path fullPath = Path.of(dir, target);
                                if (Files.isRegularFile(fullPath) && Files.isExecutable(fullPath)) {
                                    outStr = target + " is " + fullPath.toString();
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            outStr = target + ": not found";
                        }
                    }
                    if (outStr != null) {
                        printOrRedirect(outStr, stdoutRedirectFile, appendStdout, stderrRedirectFile, appendStderr,
                                targetStream);
                    }
                }
            } else {
                String pathEnv = System.getenv("PATH");
                boolean found = false;

                if (pathEnv != null) {
                    String[] directories = pathEnv.split(File.pathSeparator);
                    for (String dir : directories) {
                        Path fullPath = Path.of(dir, command);
                        if (Files.isRegularFile(fullPath) && Files.isExecutable(fullPath)) {
                            found = true;
                            break;
                        }
                    }
                }

                if (found) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(new File(System.getProperty("user.dir")));

                        if (stdoutRedirectFile != null) {
                            File f = new File(stdoutRedirectFile);
                            if (appendStdout) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(f));
                            } else {
                                pb.redirectOutput(f);
                            }
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }

                        if (stderrRedirectFile != null) {
                            File f = new File(stderrRedirectFile);
                            if (appendStderr) {
                                pb.redirectError(ProcessBuilder.Redirect.appendTo(f));
                            } else {
                                pb.redirectError(f);
                            }
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }

                        Process process = pb.start();

                        if (runInBackground) {
                            int jobId = 1;
                            for (Job job : backgroundJobs) {
                                if (job.id >= jobId) {
                                    jobId = job.id + 1;
                                }
                            }
                            System.out.println("[" + jobId + "] " + process.pid());

                            StringBuilder cmdBuilder = new StringBuilder();
                            for (int j = 0; j < partsList.size(); j++) {
                                cmdBuilder.append(partsList.get(j));
                                if (j < partsList.size() - 1)
                                    cmdBuilder.append(" ");
                            }

                            backgroundJobs
                                    .add(new Job(jobId, process.pid(), cmdBuilder.toString(), "Running", process));
                        } else {
                            process.waitFor();
                        }

                    } catch (Exception e) {
                        System.out.println("Error executing program: " + e.getMessage());
                    }
                } else {
                    printOrRedirect(command + ": command not found", stdoutRedirectFile, appendStdout,
                            stderrRedirectFile, appendStderr, 2);
                }
            }
        }
    }

    // --- NEW HELPER METHODS ---
    private static boolean isBuiltinCommand(String cmd) {
        return cmd.equals("exit") || cmd.equals("echo") || cmd.equals("pwd") ||
                cmd.equals("cd") || cmd.equals("type") || cmd.equals("jobs");
    }

    private static String executeBuiltinForPipeline(List<String> parts) {
        String command = parts.get(0);
        if (command.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.size(); i++) {
                sb.append(parts.get(i));
                if (i < parts.size() - 1)
                    sb.append(" ");
            }
            return sb.toString();
        } else if (command.equals("pwd")) {
            return System.getProperty("user.dir");
        } else if (command.equals("type")) {
            if (parts.size() > 1) {
                String target = parts.get(1);
                if (isBuiltinCommand(target)) {
                    return target + " is a shell builtin";
                } else {
                    String pathEnv = System.getenv("PATH");
                    if (pathEnv != null) {
                        String[] directories = pathEnv.split(File.pathSeparator);
                        for (String dir : directories) {
                            Path fullPath = Path.of(dir, target);
                            if (Files.isRegularFile(fullPath) && Files.isExecutable(fullPath)) {
                                return target + " is " + fullPath.toString();
                            }
                        }
                    }
                    return target + ": not found";
                }
            }
        }
        return null;
    }
    // --------------------------

    private static void checkAndReapJobs(boolean printAll) {
        int size = backgroundJobs.size();
        List<Job> completedJobs = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            Job job = backgroundJobs.get(i);

            char marker = ' ';
            if (i == size - 1) {
                marker = '+';
            } else if (i == size - 2) {
                marker = '-';
            }

            if (!job.process.isAlive()) {
                job.status = "Done";
                System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, job.status, job.command);
                completedJobs.add(job);
            } else if (printAll) {
                System.out.printf("[%d]%c  %-24s%s &\n", job.id, marker, job.status, job.command);
            }
        }

        backgroundJobs.removeAll(completedJobs);
    }

    private static void printOrRedirect(String output, String stdoutFile, boolean appendStdout, String stderrFile,
            boolean appendStderr, int streamType) throws Exception {
        if (streamType == 2) {
            if (stderrFile != null) {
                File f = new File(stderrFile);
                if (appendStderr) {
                    Files.writeString(f.toPath(), output + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } else {
                    Files.writeString(f.toPath(), output + "\n");
                }
            } else {
                System.err.println(output);
            }
        } else {
            if (stdoutFile != null) {
                File f = new File(stdoutFile);
                if (appendStdout) {
                    Files.writeString(f.toPath(), output + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } else {
                    Files.writeString(f.toPath(), output + "\n");
                }
            } else {
                System.out.println(output);
            }
        }
    }

    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escapeNext = false;
        boolean building = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escapeNext) {
                currentArg.append(c);
                escapeNext = false;
                continue;
            }

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    currentArg.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '\\' || next == '"' || next == '$' || next == '`' || next == '\n') {
                        currentArg.append(next);
                        i++;
                    } else {
                        currentArg.append(c);
                    }
                } else if (c == '"') {
                    inDoubleQuotes = false;
                } else {
                    currentArg.append(c);
                }
            } else {
                if (c == '\\') {
                    escapeNext = true;
                    building = true;
                } else if (c == '\'') {
                    inSingleQuotes = true;
                    building = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                    building = true;
                } else if (c == ' ' || c == '\t') {
                    if (building) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                        building = false;
                    }
                } else {
                    currentArg.append(c);
                    building = true;
                }
            }
        }

        if (building) {
            args.add(currentArg.toString());
        }

        return args;
    }
}