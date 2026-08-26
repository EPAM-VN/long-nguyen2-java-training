package epam.training.demo.user;

// Constants deliberately don't carry the "ROLE_" prefix - Spring Security's
// hasRole("ADMIN") adds that prefix implicitly when checking, but wherever
// we build a SimpleGrantedAuthority by hand (UserDetailsServiceImpl), it has
// to be added explicitly - forgetting it is a classic gotcha.
public enum Role {
    USER,
    ADMIN
}
