package training.taskforge.util;

import training.taskforge.model.Job;

import java.util.List;

public record LoadResult(List<Job> jobs, List<String> rejectedRows) {

    // Constructor that makes defensive copies of the lists to ensure immutability.
    public LoadResult {
        jobs = List.copyOf(jobs);
        rejectedRows = List.copyOf(rejectedRows);
    }

    public boolean hasRejections() {
        return !rejectedRows.isEmpty();
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Loaded %d jobs (%d rejected).".formatted(jobs.size(), rejectedRows.size()));
        if (hasRejections()) {
            rejectedRows.forEach(row -> builder.append(" \n rejected %s".formatted(row)));
        }
        return builder.toString();
    }
}
