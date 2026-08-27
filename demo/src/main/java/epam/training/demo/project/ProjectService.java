package epam.training.demo.project;

import epam.training.demo.common.NotFoundException;
import epam.training.demo.project.dto.ProjectCreateRequest;
import epam.training.demo.project.dto.ProjectUpdateRequest;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.springframework.security.core.Authentication;
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

    // Query-level filtering, not @PreAuthorize: an annotation can gate
    // whether this endpoint is reachable at all, but it can't decide which
    // rows come back - a non-admin only ever sees their own projects, an
    // admin sees everything.
    @Transactional(readOnly = true)
    public List<Project> findAll(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return projectRepository.findAllWithTasks();
        }
        return projectRepository.findAllWithTasksByOwnerUsername(authentication.getName());
    }

    @Transactional(readOnly = true)
    public Project findById(Long id) {
        return projectRepository.findByIdWithTasks(id)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(id)));
    }

    // Owner is always the authenticated caller, never a client-supplied id
    // (see ProjectCreateRequest) - a real admin-on-behalf-of flow would be
    // a distinct, explicitly hasRole('ADMIN')-gated endpoint, not a body
    // field on the default create path.
    @Transactional
    public Project create(ProjectCreateRequest request, Authentication authentication) {
        User owner = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User '%s' not found".formatted(authentication.getName())));

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

    public List<Project> createBatchUnsafe(List<ProjectCreateRequest> requests, Authentication authentication) {
        List<Project> created = new ArrayList<>();
        for (ProjectCreateRequest request : requests) {
            created.add(create(request, authentication));
        }
        throw new RuntimeException(
                "createBatchUnsafe: unconditional failure after processing %d requests".formatted(requests.size()));
    }
}
