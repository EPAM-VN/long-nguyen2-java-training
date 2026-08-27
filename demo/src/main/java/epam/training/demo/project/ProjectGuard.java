package epam.training.demo.project;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Bean name defaults to "projectGuard" (decapitalized class name) - that's
// what @PreAuthorize's SpEL expressions reference via @projectGuard.
@Component
public class ProjectGuard {

    private final ProjectRepository projectRepository;

    public ProjectGuard(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // Returns false, never throws, when the project doesn't exist - this
    // is deliberate, not an oversight. If this threw NotFoundException
    // instead, a non-owner probing ids could tell "404 = doesn't exist"
    // apart from "403 = exists but isn't yours", which lets them enumerate
    // real project ids they have zero access to. Returning false collapses
    // both cases into the same 403, so this guard can never become an
    // existence oracle. @Transactional(readOnly = true) because
    // project.getOwner() is a lazy association, and @PreAuthorize
    // evaluation runs before the controller method (and any of its own
    // @Transactional) even starts.
    @Transactional(readOnly = true)
    public boolean isOwner(Long projectId, Authentication authentication) {
        return projectRepository.findById(projectId)
                .map(project -> project.getOwner().getUsername().equals(authentication.getName()))
                .orElse(false);
    }
}
