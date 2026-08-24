package epam.training.demo.project;

import epam.training.demo.project.dto.ProjectCreateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectBatchService {

    private final ProjectService projectService;

    public ProjectBatchService(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Transactional
    public List<Project> createBatch(List<ProjectCreateRequest> requests) {
        List<Project> created = new ArrayList<>();
        for (ProjectCreateRequest request : requests) {
            created.add(projectService.create(request));
        }
        throw new RuntimeException(
                "createBatch: unconditional failure after processing %d requests".formatted(requests.size()));
    }
}
