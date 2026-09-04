package epam.training.demo.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Deliberately plain columns (taskId/projectId as raw ids), not @ManyToOne
// associations like every other entity here - this row has to survive
// independently of whatever happens to the Task/Project rows in the same
// request (see TaskConflictLogService), so it doesn't need a live,
// lazy-loadable relationship back to them, just enough to identify what the
// conflict was about.
@Entity
@Table(name = "task_conflict_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class TaskConflictLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "attempted_version", nullable = false)
    private Long attemptedVersion;

    @Column(name = "actual_version", nullable = false)
    private Long actualVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
