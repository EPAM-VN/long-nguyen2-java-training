package epam.training.demo.security;

import epam.training.demo.security.dto.UserResponse;
import epam.training.demo.user.User;
import org.mapstruct.Mapper;

// The trivial case: every UserResponse field (id, username, email,
// createdAt) matches a same-named User property exactly, so this needs no
// @Mapping annotations at all - MapStruct wires it all up by name on its
// own.
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
