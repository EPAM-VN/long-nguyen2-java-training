package training.taskforge.queue;

import training.taskforge.model.Job;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// all the jobs that have been registered in the system
public class JobRegistry {
    // use a ConcurrentHashMap to allow concurrent access to the registry
    // jobs are registered from the CLI thread while workers read from it concurrently
    private final Map<UUID, Job> byId = new ConcurrentHashMap<>();

    public void register(Job job) {
        byId.put(job.id(), job);
    }

    public void registerAll(Collection<? extends Job> jobs) {
        jobs.forEach(this::register);
    }

    public Optional<Job> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    // All jobs in natural order (priority desc, createdAt asc, seq asc)
    public NavigableSet<Job> sortedByPriorityDesc() {
        // Copy into a TreeSet, which sorts on insert using Job.compareTo.
        return new TreeSet<>(byId.values());
    }

    // All jobs in order of their sequence number (seq asc)
    public NavigableMap<Long, Job> bySequence() {
        return byId.values().stream()
                .collect(Collectors.toMap(Job::seq, job -> job, (a, b) -> a, TreeMap::new));
    }

    public int size() {
        return byId.size();
    }
}
