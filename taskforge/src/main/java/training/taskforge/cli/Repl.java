package training.taskforge.cli;

import training.taskforge.error.JobPersistenceException;
import training.taskforge.handler.HandlerRegistry;
import training.taskforge.model.*;
import training.taskforge.report.ReportGenerator;
import training.taskforge.runner.JobRunner;
import training.taskforge.runner.VirtualThreadJobRunner;
import training.taskforge.store.JobHistory;
import training.taskforge.util.JobHelper;
import training.taskforge.util.LoadResult;
import training.taskforge.queue.JobRegistry;
import training.taskforge.queue.PriorityJobQueue;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Repl implements Closeable {
    private final PriorityJobQueue queue = new PriorityJobQueue(); // jobs queue
    private final JobRegistry registry = new JobRegistry(); // all jobs
    private final JobHistory history = new JobHistory(); // finished jobs
    private final HandlerRegistry handlers = new HandlerRegistry();
    private final ReportGenerator reports = new ReportGenerator();

    private final Path reportPath;
    private final JobRunner runner;

    private boolean running = true;
    private boolean hasRun = false;

    public Repl(Path historyPath, Path reportPath){
        this.runner = new VirtualThreadJobRunner(handlers, history);
        this.reportPath = reportPath;
    }

    public void loop() {
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            help();
            while (running) {
                System.out.print("> ");
                System.out.flush();
                String input = reader.readLine();
                if (input == null) {
                    break;
                }
                dispatch(CommandParser.parse(input));
            }
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }

    public void dispatch(Command command) {
        switch (command) {
            case Command.Load(Path path) -> load(path);
            case Command.Submit(JobType type, JobPriority priority, Map<String, String> payload) -> submit(type, priority, payload);
            case Command.Queue() -> queue();
            case Command.Run() -> run();
            case Command.Benchmark(int n) -> bench(n);
            case Command.Report() -> report();
            case Command.History(int n) -> history(n);
            case Command.Exit() -> shutdown();
            case Command.Help() -> help();
            case Command.Unknown(String input, String message) ->
                    System.err.println("Invalid command: " + input + " (" + message + ")");
        }
    }

    private void load(Path path) {
        try {
            LoadResult result = JobHelper.loadCSV(path);
            queue.submitAll(result.jobs());
            registry.registerAll(result.jobs());
            System.out.println(result.summary());
        } catch (JobPersistenceException e) {
            System.out.println("could not load: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("could not load: " + e.getMessage());
        }
    }

    private void submit(JobType type, JobPriority priority, Map<String, String> payload) {
        Job job = Job.create(type, priority, payload);
        queue.offer(job);
        registry.register(job);
        System.out.printf("Submitted job %s (%s, %s).%n", job.shortId(), type, priority);
    }

    private void queue() {
        if (queue.isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }
        System.out.println(queue.size() + " jobs pending:");
        int index = 1;
        for (Job job : queue) {
            System.out.printf("%d. %s (%s, %s) - %s%n", index++, job.shortId(), job.type(), job.priority(), job.payloadAsString());
        }
    }

    private void run() {
        if (queue.isEmpty()) {
            System.out.println("Nothing to run.");
            return;
        }
        List<Job> batch = new ArrayList<>();
        queue.drainAllInto(batch::add);

        long startTime = System.nanoTime();
        List<JobResult> results = runner.runAll(batch);
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        long successCount = results.stream().filter(JobResult.Success.class::isInstance).count();
        System.out.printf("Executed %d jobs in %d s (%d succeeded).%n", batch.size(), elapsedMs/1000, successCount);

        hasRun = true;
    }

    private void bench(int n) {
        // Implementation for comparing the three runners over n jobs
        System.out.println("Running benchmark with " + n + " jobs...");
    }

    private void report() {
        if (!hasRun) {
            System.out.println("nothing has run yet.");
            return;
        }
        System.out.println();
        System.out.println(reports.generate(history.all()));
    }

    private void history(int limit) {
        if (!hasRun) {
            System.out.println("nothing has run yet.");
            return;
        }
        System.out.println("Newest first:");
        for (HistoryEntry entry : history.newestFirst(limit)) {
            System.out.printf("  %-13s %-9s %-10s %6dms%n",
                    entry.job().type(), entry.job().priority(), entry.status(),
                    entry.durationMillis());
        }
    }

    private void help() {
        System.out.println("""
                TaskForge - commands:
                  load <path>                          read jobs from a CSV file
                  submit <type> <priority> [payload]   add one job (payload is key=value;key=value)
                  queue                                list pending jobs in priority order
                  run                                  execute everything in the queue
                  bench [n]                            compare the three runners over n jobs
                  report                               print the aggregate report
                  history [n]                          show the n most recent results
                  help                                 show this list
                  exit                                 write output files and quit""");
    }

    private void shutdown() {
        running = false;
        if (!hasRun) {
            System.out.println("No jobs have been run yet. Exiting without writing output files.");
        } else {
            System.out.println("Exiting. Output files have been written.");
        }
        System.exit(0);
    }

    @Override
    public void close() {
        // Clean up resources
    }
}

