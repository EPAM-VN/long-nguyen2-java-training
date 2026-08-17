package training.taskforge.queue;

import training.taskforge.model.Job;

import java.util.*;
import java.util.function.Consumer;

public class PriorityJobQueue implements Iterable<Job>{
    private final PriorityQueue<Job> heap = new PriorityQueue<>();

    public void offer(Job job) {
        heap.offer(job);
    }

    public Optional<Job> poll() {
        return Optional.ofNullable(heap.poll());
    }

    public void submitAll(Collection<? extends Job> jobs) {
        jobs.forEach(this::offer);
    }

    // Drains the queue into the provided sink, one job at a time.
    public void drainInto(Consumer<? super Job> sink) {
        poll().ifPresent(sink);
    }

    // Drains the entire queue into the provided sink, one job at a time.
    public void drainAllInto(Consumer<? super Job> sink) {
        while (!heap.isEmpty()) {
            drainInto(sink);
        }
    }

    public int size() {
        return heap.size();
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    // Returns a snapshot of the current jobs in the queue, sorted in natural order (priority desc, createdAt asc, seq asc).
    public List<Job> peekAll() {
        List<Job> snapshot = new ArrayList<>(heap);
        snapshot.sort(Comparator.naturalOrder());
        return List.copyOf(snapshot);
    }

    @Override
    public Iterator<Job> iterator() {
        List<Job> ordered = peekAll();   // snapshot up front, so mutation during iteration is safe
        return ordered.iterator();
    }
}
