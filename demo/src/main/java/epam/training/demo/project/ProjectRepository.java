package epam.training.demo.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN FETCH p.tasks")
    List<Project> findAllWithTasks();

    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN FETCH p.tasks WHERE p.id = :id")
    Optional<Project> findByIdWithTasks(@Param("id") Long id);
}
