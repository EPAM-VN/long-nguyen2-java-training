package training.taskforge.store;

import training.taskforge.model.HistoryEntry;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class JobHistory implements Iterable<HistoryEntry> {
    private final List<HistoryEntry> entries = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void record(Job job, JobResult result) {
        HistoryEntry entry = new HistoryEntry(Instant.now(), job, result);
        lock.lock();
        try {
            entries.add(entry);
        } finally {
            lock.unlock();
        }
    }

    public List<HistoryEntry> newestFirst(int limit) {
        List<HistoryEntry> snapshot;
        lock.lock();
        try {
            snapshot = List.copyOf(entries);
        } finally {
            lock.unlock();
        }
        return snapshot.reversed().stream()
                .limit(limit)
                .toList();
    }

    public Optional<HistoryEntry> mostRecent() {
        lock.lock();
        try {
            return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getLast());
        } finally {
            lock.unlock();
        }
    }

    public List<HistoryEntry> all() {
        lock.lock();
        try {
            return List.copyOf(entries);
        } finally {
            lock.unlock();
        }
    }

    public void addAll(List<HistoryEntry> loaded) {
        lock.lock();
        try {
            entries.addAll(loaded);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Iterator<HistoryEntry> iterator() {
        return all().iterator();
    }
}
