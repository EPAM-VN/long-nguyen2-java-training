package training.taskforge.runner;

import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

import java.util.List;

public interface JobRunner {
    List<JobResult> runAll(List<Job> jobs);
    long processedCount();
    String name();
}
