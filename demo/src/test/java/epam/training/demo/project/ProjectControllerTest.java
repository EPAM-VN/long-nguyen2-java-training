package epam.training.demo.project;

import epam.training.demo.common.NotFoundException;
import epam.training.demo.project.dto.ProjectCreateRequest;
import epam.training.demo.project.dto.ProjectUpdateRequest;
import epam.training.demo.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// NOTE ON WHAT THIS SLICE CANNOT PROVE: MethodSecurityConfig below enables
// method security (@PreAuthorize evaluation) but does NOT import the real
// SecurityConfig/SecurityFilterChain - no CSRF handling, no JWT resource
// server, no ProblemDetailAuthenticationEntryPoint. So while this class can
// prove 403 (access denied from @PreAuthorize) is correct, it cannot prove
// what an actual 401 (unauthenticated) response body looks like in this
// app - that depends on ProblemDetailAuthenticationEntryPoint, which only
// exists in the real filter chain. That's Step 11.11's job, not this one.
@WebMvcTest(ProjectController.class)
@ActiveProfiles("test")
@Import(ProjectControllerTest.MethodSecurityConfig.class)
class ProjectControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean(name = "projectGuard")
    private ProjectGuard projectGuard;

    // ProjectResponse.from() dereferences project.getOwner().getId() and
    // project.getTasks().size() - a bare `new Project()` returned from a
    // mock NPEs for a reason that has nothing to do with whatever a given
    // test is actually checking, so every stub returns a fully populated
    // entity instead.
    private Project fullyPopulatedProject(Long id, String name, Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setUsername("owner" + ownerId);

        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setDescription("A description");
        project.setOwner(owner);
        project.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return project;
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getAll_returnsProjectListShape() throws Exception {
        Project project = fullyPopulatedProject(1L, "Project One", 10L);
        when(projectService.findAll(any(Authentication.class))).thenReturn(List.of(project));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Project One"))
                .andExpect(jsonPath("$[0].ownerId").value(10))
                .andExpect(jsonPath("$[0].taskCount").value(0));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getById_asOwner_returns200() throws Exception {
        Project project = fullyPopulatedProject(1L, "Project One", 10L);
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        when(projectService.findById(1L)).thenReturn(project);

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // This test was the target of the deliberate missing-method-security
    // reproduction, and it took two tries to actually reproduce anything:
    //
    // 1. First removed @Import(MethodSecurityConfig.class) and re-ran -
    //    still got the SAME failure as with it present (see #2 below), not
    //    the expected 200. Turns out @Import wasn't doing anything: Spring
    //    Test auto-detects a static, non-private, non-final, nested
    //    @Configuration/@TestConfiguration class as a "default
    //    configuration class" regardless of @Import (the exact mechanism
    //    named in the "Could not detect default configuration classes..."
    //    log line every @DataJpaTest in this project prints). So
    //    MethodSecurityConfig, and @EnableMethodSecurity with it, was
    //    active the whole time either way.
    // 2. With @Import restored, this test failed with 500, not 403 or
    //    200: NoSuchBeanDefinitionException - "No bean named 'projectGuard'
    //    available", thrown while evaluating the @projectGuard SpEL bean
    //    reference. @MockitoBean without an explicit name apparently
    //    doesn't register under the field name when it creates a brand
    //    NEW bean (ProjectGuard has no matching bean in this slice to
    //    override) in this Spring Boot 4.1.1 / spring-test 7.0.9
    //    combination - fixed by adding name = "projectGuard" above.
    // 3. To actually reproduce missing method security, removed
    //    @EnableMethodSecurity itself (not @Import) from
    //    MethodSecurityConfig and re-ran: NOW it failed with 500 again,
    //    but for yet another reason - a NullPointerException from
    //    ProjectResponse.from(null), because with @PreAuthorize never
    //    evaluated at all, getById() ran completely unguarded and called
    //    projectService.findById(1L), which this test deliberately never
    //    stubs (a correctly-guarded request should never reach it) - so
    //    Mockito's default null return blew up downstream instead of a
    //    clean 200. Restoring @EnableMethodSecurity turned this back to
    //    green for the right reason.
    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getById_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getById_asAdmin_returns200RegardlessOfOwnership() throws Exception {
        Project project = fullyPopulatedProject(1L, "Project One", 10L);
        when(projectService.findById(1L)).thenReturn(project);

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk());

        // projectGuard.isOwner deliberately never stubbed - if the SpEL
        // "hasRole('ADMIN') or @projectGuard.isOwner(...)" didn't
        // short-circuit on the true left-hand side, this would still
        // return 200 (OR is true either way), so the 200 status alone
        // wouldn't prove short-circuiting. This does.
        verifyNoInteractions(projectGuard);
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void create_validBody_returns201WithLocationHeader() throws Exception {
        Project created = fullyPopulatedProject(5L, "New Project", 10L);
        when(projectService.create(any(ProjectCreateRequest.class), any(Authentication.class))).thenReturn(created);

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Project","description":"A description"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/projects/5")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void create_nameWithLeadingOrTrailingWhitespace_returns400() throws Exception {
        // Day 7's @NoLeadingOrTrailingWhitespace constraint, proven for the
        // first time through the full web layer: @Valid @RequestBody
        // binding/validation runs before the controller method body (and
        // before ProjectService.create is ever called), so this 400 comes
        // from MethodArgumentNotValidException, not from anything
        // hand-written here.
        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  x  ","description":"A description"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void update_asOwner_returns200() throws Exception {
        Project updated = fullyPopulatedProject(1L, "Updated Name", 10L);
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        when(projectService.update(eq(1L), any(ProjectUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/projects/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Name","description":"A description"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void update_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(put("/api/projects/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Name","description":"A description"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void delete_asOwner_returns204() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);

        mockMvc.perform(delete("/api/projects/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(projectService).delete(1L);
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void delete_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(delete("/api/projects/1").with(csrf()))
                .andExpect(status().isForbidden());

        verify(projectService, never()).delete(any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getById_notFound_returns404ProblemDetail() throws Exception {
        // Proves GlobalExceptionHandler (@RestControllerAdvice) is live in
        // this slice without any extra wiring - @WebMvcTest includes
        // @ControllerAdvice beans by default, unlike plain @Component
        // beans.
        when(projectService.findById(99L)).thenThrow(new NotFoundException("Project 99 not found"));

        mockMvc.perform(get("/api/projects/99"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Project 99 not found"));
    }
}
