package epam.training.demo.project.dto;

import epam.training.demo.common.validation.NoLeadingOrTrailingWhitespace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No id, no createdAt, no ownerId - a client never sets any of those.
// ownerId in particular used to be here (pre-authentication, Day 7) but
// letting the client assert who owns the thing it's creating would defeat
// the ownership guard entirely now that "owner" is a real security
// boundary - the owner is always the authenticated caller
// (ProjectService.create derives it from Authentication.getName()).
public record ProjectCreateRequest(

        @NotBlank
        @Size(max = 100)
        @NoLeadingOrTrailingWhitespace
        String name,

        @Size(max = 2000)
        String description
) {
}
