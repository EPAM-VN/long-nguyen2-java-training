package epam.training.demo.task;

import epam.training.demo.config.PaginationConfig;
import epam.training.demo.config.TaskflowProperties;
import epam.training.demo.project.Project;
import epam.training.demo.project.ProjectGuard;
import epam.training.demo.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Same 11.9 caveat applies here: MethodSecurityConfig enables @PreAuthorize
// evaluation but does not import the real SecurityFilterChain, so this
// slice cannot prove what a real 401 body looks like
// (ProblemDetailAuthenticationEntryPoint only exists in the full filter
// chain) - that's Step 11.11's job.
@WebMvcTest(TaskController.class)
@ActiveProfiles("test")
@Import({TaskControllerTest.MethodSecurityConfig.class, PaginationConfig.class})
@EnableConfigurationProperties(TaskflowProperties.class)
class TaskControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private ProjectService projectService;

    // Explicit names, learned the hard way in 11.9: @MockitoBean without
    // one doesn't register under the field name when it's creating a new
    // bean rather than overriding an existing one - and @PreAuthorize's
    // SpEL bean references (@projectGuard, @taskGuard) need the bean
    // findable by exactly that name.
    @MockitoBean(name = "projectGuard")
    private ProjectGuard projectGuard;

    @MockitoBean(name = "taskGuard")
    private TaskGuard taskGuard;

    private Task fullyPopulatedTask(Long id, Long projectId, String title) {
        Project project = new Project();
        project.setId(projectId);

        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(Priority.MEDIUM);
        task.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        task.setVersion(0L);
        task.setProject(project);
        return task;
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getAll_asOwner_returnsPagedShape() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        Task task = fullyPopulatedTask(1L, 1L, "Task One");
        Page<Task> page = new PageImpl<>(List.of(task), PageRequest.of(0, 20), 1);
        when(taskService.findAll(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        // Printed once, on purpose, before asserting anything - Boot 4's
        // actual paged-JSON shape (content/totalElements/... vs some other
        // field naming) is worth seeing rather than guessing.
        mockMvc.perform(get("/api/projects/1/tasks"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Task One"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getAll_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/projects/1/tasks"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getAll_sizeExceedsMax_isClampedNotRejected() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        when(taskService.findAll(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        // Confirmed, not assumed: PageableHandlerMethodArgumentResolver's
        // setMaxPageSize(...) (wired by `PaginationConfig` from
        // taskflow.max-page-size = 100) silently CLAMPS an oversized
        // ?size= down to the max - it does not reject the request with a
        // 400, and it does not throw. The 200 below and the captured page
        // size of 100 are both part of the same confirmation.
        mockMvc.perform(get("/api/projects/1/tasks?size=5000"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(taskService).findAll(eq(1L), isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void statusCounts_asOwner_returns200() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        when(projectService.findById(1L)).thenReturn(new Project());
        when(taskRepository.countByStatusForProject(1L))
                .thenReturn(List.of(new TaskStatusCount(TaskStatus.TODO, 3L)));

        mockMvc.perform(get("/api/projects/1/tasks/status-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("TODO"))
                .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void statusCounts_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/projects/1/tasks/status-counts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void search_asOwner_returns200() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        when(projectService.findById(1L)).thenReturn(new Project());
        Task task = fullyPopulatedTask(1L, 1L, "Quarterly report");
        when(taskRepository.searchByTitleForProject(1L, "report")).thenReturn(List.of(task));

        mockMvc.perform(get("/api/projects/1/tasks/search").param("keyword", "report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Quarterly report"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void search_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/projects/1/tasks/search").param("keyword", "report"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void summary_asOwner_returns200() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        when(projectService.findById(1L)).thenReturn(new Project());

        // TaskSummary is a Spring Data projection interface - normally a
        // runtime proxy over a query result, not a class you can `new`.
        // First attempt used Mockito.mock(TaskSummary.class) - it compiled
        // and stubbed fine, but Jackson serializing it produced a huge,
        // recursively self-referential blob of Mockito's own internal
        // state (a "mockitoInterceptor" field and everything under it),
        // because Jackson's bean introspection picks up every
        // getter-shaped method on the generated proxy class, not just the
        // three real interface accessors. A mock is fine to stub and
        // verify against, but never safe to hand to a JSON serializer. A
        // trivial anonymous implementation has none of that baggage.
        TaskSummary summary = new TaskSummary() {
            @Override
            public Long getId() {
                return 1L;
            }

            @Override
            public String getTitle() {
                return "Task One";
            }

            @Override
            public TaskStatus getStatus() {
                return TaskStatus.TODO;
            }
        };
        when(taskRepository.findByProjectId(1L, TaskSummary.class)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/projects/1/tasks/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Task One"))
                .andExpect(jsonPath("$[0].status").value("TODO"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void summary_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/projects/1/tasks/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void create_asOwner_returns201WithLocation() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        Task created = fullyPopulatedTask(99L, 1L, "New Task");
        when(taskService.create(eq(1L), any(epam.training.demo.task.dto.TaskCreateRequest.class)))
                .thenReturn(created);

        mockMvc.perform(post("/api/projects/1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Task"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/projects/1/tasks/99")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void create_asNonOwner_returns403() throws Exception {
        when(projectGuard.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(post("/api/projects/1/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Task"}
                                """))
                .andExpect(status().isForbidden());
    }

    // PUT/DELETE use @taskGuard, NOT @projectGuard - stubbing the wrong
    // guard here would compile fine and silently always deny (Mockito's
    // default for an unstubbed boolean method is false), which is exactly
    // the trap having two guards on one controller invites. Every
    // update/delete test below stubs taskGuard specifically.
    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void update_asOwner_returns200() throws Exception {
        when(taskGuard.isOwner(eq(5L), any(Authentication.class))).thenReturn(true);
        Task updated = fullyPopulatedTask(5L, 1L, "Updated Task");
        when(taskService.update(eq(1L), eq(5L), any(epam.training.demo.task.dto.TaskUpdateRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/projects/1/tasks/5")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated Task","status":"TODO","priority":"MEDIUM","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Task"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void update_asNonOwner_returns403() throws Exception {
        when(taskGuard.isOwner(eq(5L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(put("/api/projects/1/tasks/5")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated Task","status":"TODO","priority":"MEDIUM","version":0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void delete_asOwner_returns204() throws Exception {
        when(taskGuard.isOwner(eq(5L), any(Authentication.class))).thenReturn(true);

        mockMvc.perform(delete("/api/projects/1/tasks/5").with(csrf()))
                .andExpect(status().isNoContent());

        verify(taskService).delete(1L, 5L);
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void delete_asNonOwner_returns403() throws Exception {
        when(taskGuard.isOwner(eq(5L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(delete("/api/projects/1/tasks/5").with(csrf()))
                .andExpect(status().isForbidden());

        verify(taskService, never()).delete(any(), any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getAll_invalidStatusEnumValue_returns400() throws Exception {
        // projectGuard deliberately not stubbed: @RequestParam TaskStatus
        // status is converted by Spring MVC's argument resolution, which
        // runs before the @PreAuthorize-guarded method invocation even
        // starts - so this never reaches the guard either way.
        mockMvc.perform(get("/api/projects/1/tasks?status=BANANA"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
