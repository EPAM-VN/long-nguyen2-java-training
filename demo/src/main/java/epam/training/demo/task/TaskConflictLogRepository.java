package epam.training.demo.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskConflictLogRepository extends JpaRepository<TaskConflictLog, Long> {

    List<TaskConflictLog> findByTaskId(Long taskId);
}
