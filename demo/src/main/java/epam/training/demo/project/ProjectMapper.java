package epam.training.demo.project;

import epam.training.demo.project.dto.ProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// componentModel = "spring" is what makes MapStruct annotate the generated
// ProjectMapperImpl with @Component, so it's a normal injectable bean
// (constructor-injected into ProjectController) instead of something built
// via Mappers.getMapper(...).
@Mapper(componentModel = "spring")
public interface ProjectMapper {

    // ownerId has no field of that name on Project to match by name -
    // "owner.id" tells MapStruct to navigate project.getOwner().getId()
    // (with a generated null check on getOwner() first, unlike the
    // original hand-written ProjectResponse.from(), which dereferenced it
    // unguarded).
    @Mapping(target = "ownerId", source = "owner.id")
    // Not a real property on Project - taskCount is derived, so this falls
    // back to a plain Java expression instead of a source path. `project`
    // here refers to this method's own parameter, in scope for the
    // generated code.
    @Mapping(target = "taskCount", expression = "java(project.getTasks().size())")
    ProjectResponse toResponse(Project project);
}
