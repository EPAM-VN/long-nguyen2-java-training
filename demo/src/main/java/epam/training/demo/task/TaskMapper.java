package epam.training.demo.task;

import epam.training.demo.task.dto.TaskResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    // assignee is a genuinely nullable association (unassigned tasks are
    // normal) - MapStruct generates the exact same null guard the original
    // hand-written TaskResponse.from() had
    // (task.getAssignee() == null ? null : task.getAssignee().getId()),
    // just derived from the "assignee.id" path instead of written by hand.
    @Mapping(target = "assigneeId", source = "assignee.id")
    @Mapping(target = "projectId", source = "project.id")
    TaskResponse toResponse(Task task);
}
