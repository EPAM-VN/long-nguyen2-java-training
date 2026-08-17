package training.taskforge;

import training.taskforge.cli.Repl;

import java.nio.file.Path;

public class Main {
    private static final Path DEFAULT_HISTORY = Path.of("data", "job-history.log");
    private static final Path DEFAULT_REPORT = Path.of("data", "report.txt");

    public static void main(String[] args) {
        Path historyPath = args.length > 0 ? Path.of(args[0]) : DEFAULT_HISTORY;
        Path reportPath = args.length > 1 ? Path.of(args[1]) : DEFAULT_REPORT;

        try (var repl = new Repl(historyPath, reportPath)) {
            repl.loop();
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            System.exit(1);
        }
    }
}