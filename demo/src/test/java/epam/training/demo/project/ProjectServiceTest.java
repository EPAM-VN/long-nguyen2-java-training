package epam.training.demo.project;

import epam.training.demo.common.NotFoundException;
import epam.training.demo.project.dto.ProjectCreateRequest;
import epam.training.demo.project.dto.ProjectUpdateRequest;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    @DisplayName("BROKEN by design: argThat(p -> p.equals(new Project())) passes no matter what save() was called with")
    void create_brokenAssertionPassesForTheWrongReason() {
        // Project only overrides equals()/hashCode() on id (@EqualsAndHashCode(of = "id")).
        // Every transient (unsaved) Project has a null id, so `new Project().equals(anyOtherUnsavedProject)`
        // is ALWAYS true - this assertion can never fail regardless of what
        // save() actually received. Proven here by feeding create() a
        // request whose values contradict what a naive reader might expect
        // this test to check for, and watching it pass anyway.
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectCreateRequest request = new ProjectCreateRequest("Real Name", "Real description");
        Authentication authentication = new TestingAuthenticationToken("alice", null);

        projectService.create(request, authentication);

        // Nothing about "Wrong Name" was ever passed anywhere - this only
        // passes because entity equality is id-based, not field-based.
        verify(projectRepository).save(argThatEqualsBlankProject());
    }

    private Project argThatEqualsBlankProject() {
        return argThat(p -> p.equals(new Project()));
    }

    @Test
    @DisplayName("create() saves a Project built from the request and the authenticated owner")
    void create_savesProjectWithRequestFieldsAndOwner() {
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectCreateRequest request = new ProjectCreateRequest("Real Name", "Real description");
        Authentication authentication = new TestingAuthenticationToken("alice", null);

        Instant before = Instant.now();
        projectService.create(request, authentication);
        Instant after = Instant.now();

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Real Name");
        assertThat(saved.getDescription()).isEqualTo("Real description");
        assertThat(saved.getOwner().getUsername()).isEqualTo("alice");
        assertThat(saved.getCreatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("create() when the authenticated user doesn't exist -> NotFoundException, save() never called")
    void create_ownerNotFound_throwsAndNeverSaves() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ProjectCreateRequest request = new ProjectCreateRequest("Real Name", "Real description");
        Authentication authentication = new TestingAuthenticationToken("ghost", null);

        assertThatThrownBy(() -> projectService.create(request, authentication))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ghost");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("findAll() with ROLE_ADMIN calls findAllWithTasks(), never the owner-scoped query")
    void findAll_admin_callsFindAllWithTasks() {
        when(projectRepository.findAllWithTasks()).thenReturn(List.of());

        Authentication authentication = new TestingAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        projectService.findAll(authentication);

        verify(projectRepository, times(1)).findAllWithTasks();
        verify(projectRepository, never()).findAllWithTasksByOwnerUsername(any());
    }

    @Test
    @DisplayName("findAll() with a non-admin calls the owner-scoped query, never findAllWithTasks()")
    void findAll_nonAdmin_callsFindAllWithTasksByOwnerUsername() {
        when(projectRepository.findAllWithTasksByOwnerUsername("alice")).thenReturn(List.of());

        Authentication authentication = new TestingAuthenticationToken(
                "alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        projectService.findAll(authentication);

        verify(projectRepository, times(1)).findAllWithTasksByOwnerUsername("alice");
        verify(projectRepository, never()).findAllWithTasks();
    }

    @Test
    @DisplayName("findById() found -> returns the project")
    void findById_found_returnsProject() {
        Project project = new Project();
        project.setId(42L);
        when(projectRepository.findByIdWithTasks(42L)).thenReturn(Optional.of(project));

        Project result = projectService.findById(42L);

        assertThat(result).isSameAs(project);
    }

    @Test
    @DisplayName("findById() not found -> NotFoundException")
    void findById_notFound_throws() {
        when(projectRepository.findByIdWithTasks(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.findById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    @DisplayName("update() found -> fields updated and saved")
    void update_found_updatesFieldsAndSaves() {
        Project existing = new Project();
        existing.setId(42L);
        existing.setName("Old Name");
        existing.setDescription("Old description");
        when(projectRepository.findByIdWithTasks(42L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectUpdateRequest request = new ProjectUpdateRequest("New Name", "New description");
        Project result = projectService.update(42L, request);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New description");
        verify(projectRepository).save(existing);
    }

    @Test
    @DisplayName("update() not found -> NotFoundException, save() never called")
    void update_notFound_throwsAndNeverSaves() {
        when(projectRepository.findByIdWithTasks(42L)).thenReturn(Optional.empty());

        ProjectUpdateRequest request = new ProjectUpdateRequest("New Name", "New description");

        assertThatThrownBy(() -> projectService.update(42L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("42");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete() calls deleteById() with the given id")
    void delete_callsDeleteByIdWithGivenId() {
        projectService.delete(42L);

        verify(projectRepository).deleteById(eq(42L));
    }
}
