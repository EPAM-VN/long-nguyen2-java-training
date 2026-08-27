package epam.training.demo.project;

import epam.training.demo.project.dto.ProjectCreateRequest;
import epam.training.demo.project.dto.ProjectResponse;
import epam.training.demo.project.dto.ProjectUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // hasRole('USER') alone is enough at the annotation level - every
    // registered user already has it, so this is really just an explicit,
    // auditable statement of intent rather than a real gate. The actual
    // per-row filtering (non-admins only seeing their own projects) happens
    // in ProjectService.findAll(), not here - a @PreAuthorize can't filter
    // which rows come back.
    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public List<ProjectResponse> getAll(Authentication authentication) {
        return projectService.findAll(authentication).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN') or @projectGuard.isOwner(#id, authentication)")
    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable Long id) {
        return ProjectResponse.from(projectService.findById(id));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request,
                                                    Authentication authentication) {
        Project created = projectService.create(request, authentication);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(ProjectResponse.from(created));
    }

    @PreAuthorize("hasRole('ADMIN') or @projectGuard.isOwner(#id, authentication)")
    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest request) {
        return ProjectResponse.from(projectService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN') or @projectGuard.isOwner(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
