package training.taskforge.cli;

import training.taskforge.model.JobPriority;
import training.taskforge.model.JobType;

import java.nio.file.Path;
import java.util.Map;

public sealed interface Command {
    record Load(Path path) implements Command {}
    record Submit(JobType type, JobPriority priority, Map<String, String> payload) implements Command {
        public Submit {
            payload = Map.copyOf(payload);
        }
    }
    record Queue() implements Command {}
    record Run() implements Command {}
    record Benchmark(int n) implements Command {}
    record Report() implements Command {}
    record History(int n) implements Command {}
    record Help() implements Command {}
    record Exit() implements Command {}
    record Unknown(String input, String message) implements Command {}
}
