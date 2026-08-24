package epam.training.demo.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findByProjectId(Long projectId);

    List<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status);

    @Query("""
            select new epam.training.demo.task.TaskStatusCount(t.status, count(t))
            from Task t
            where t.project.id = :projectId
            group by t.status
            """)
    List<TaskStatusCount> countByStatusForProject(@Param("projectId") Long projectId);

    @Query(value = """
            select * from tasks
            where project_id = :projectId
              and title ilike '%' || :keyword || '%'
            """, nativeQuery = true)
    List<Task> searchByTitleForProject(@Param("projectId") Long projectId, @Param("keyword") String keyword);

    <T> List<T> findByProjectId(Long projectId, Class<T> type);
}
