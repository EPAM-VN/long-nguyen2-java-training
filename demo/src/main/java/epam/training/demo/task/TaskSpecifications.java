package epam.training.demo.task;

import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> hasProjectId(Long projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return status == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(Priority priority) {
        return priority == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> titleContains(String keyword) {
        return (keyword == null || keyword.isBlank())
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }
}
