package epam.training.demo.project;

import epam.training.demo.common.NotFoundException;
import epam.training.demo.project.dto.ProjectCreateRequest;
import epam.training.demo.project.dto.ProjectUpdateRequest;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Project> findAll() {
        return projectRepository.findAllWithTasks();
    }

    @Transactional(readOnly = true)
    public Project findById(Long id) {
        return projectRepository.findByIdWithTasks(id)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(id)));
    }

    @Transactional
    public Project create(ProjectCreateRequest request) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new NotFoundException("User %d not found".formatted(request.ownerId())));

        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwner(owner);
        project.setCreatedAt(Instant.now());
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(Long id, ProjectUpdateRequest request) {
        Project existing = findById(id);
        existing.setName(request.name());
        existing.setDescription(request.description());
        return projectRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.deleteById(id);
    }

    public List<Project> createBatchUnsafe(List<ProjectCreateRequest> requests) {
        List<Project> created = new ArrayList<>();
        for (ProjectCreateRequest request : requests) {
            created.add(create(request));
        }
        throw new RuntimeException(
                "createBatchUnsafe: unconditional failure after processing %d requests".formatted(requests.size()));
    }
}
