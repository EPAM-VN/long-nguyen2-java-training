package training.taskforge.cli;

import training.taskforge.error.InvalidJobConfigException;
import training.taskforge.util.JobHelper;
import training.taskforge.model.JobPriority;
import training.taskforge.model.JobType;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

public final class CommandParser {
    private CommandParser() {
    }

    public static Command parse(String input) {
        // Implementation for parsing command from input string
        if (input == null || input.isBlank()) {
            return new Command.Unknown("", "type 'help' to see available commands");
        }

        String[] parts = input.trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1] : "";

        return switch (command) {
            case "load" -> parseLoad(argument, input);
            case "exit" -> new Command.Exit();
            case "submit" -> parseSubmit(argument, input);
            case "queue" -> new Command.Queue();
            case "run" -> new Command.Run();
            case "benchmark", "bench" -> parseBenchmark(argument, input);
            case "report" -> new Command.Report();
            case "history", "hist" -> parseHistory(argument, input);
            case "help", "h", "?" -> new Command.Help();
            default -> new Command.Unknown(input.strip(),"unknown command '" + command + "' (try 'help')");
        };
    }

    private static Command parseLoad(String argument, String input) {
        if (argument.isBlank()) {
            return new Command.Unknown(input, "missing path argument for load command");
        }
        try {
            return new Command.Load(Path.of(argument));
        } catch (InvalidPathException e) {
            return new Command.Unknown(input, "invalid path for load command");
        }
    }

    private static Command parseSubmit(String arg, String raw) {
        if (arg.isEmpty()) {
            return new Command.Unknown(raw.strip(),
                    "usage: submit <type> <priority> [key=value;key=value]");
        }
        String[] fields = arg.split("\\s+", 3);
        if (fields.length < 2) {
            return new Command.Unknown(raw.strip(),
                    "usage: submit <type> <priority> [key=value;key=value]");
        }
        try {
            JobType type = JobType.parse(fields[0]);
            JobPriority priority = JobPriority.parse(fields[1]);
            Map<String, String> payload = fields.length == 3
                    ? JobHelper.parsePayload(fields[2])
                    : Map.of();
            return new Command.Submit(type, priority, payload);
        } catch (InvalidJobConfigException e) {
            return new Command.Unknown(raw.strip(), e.getMessage());
        }
    }

    private static Command parseHistory(String arg, String raw) {
        if (arg.isEmpty()) {
            return new Command.History(10);   // default
        }
        return parsePositiveInt(arg)
                .<Command>map(Command.History::new)
                .orElseGet(() -> new Command.Unknown(raw.strip(),
                        "history limit must be a positive number, got '" + arg + "'"));
    }

    private static Command parseBenchmark(String arg, String raw) {
        if (arg.isEmpty()) {
            return new Command.Benchmark(500);
        }
        return parsePositiveInt(arg)
                .<Command>map(Command.Benchmark::new)
                .orElseGet(() -> new Command.Unknown(raw.strip(),
                        "benchmark size must be a positive number, got '" + arg + "'"));
    }

    private static java.util.Optional<Integer> parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.strip());
            return parsed > 0 ? java.util.Optional.of(parsed) : java.util.Optional.empty();
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
