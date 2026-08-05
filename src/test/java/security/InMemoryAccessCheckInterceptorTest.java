package security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link InMemoryAccessCheckInterceptor#hasPermission(String, Set)}.
 *
 * <p>The headline case is the fail-closed fix: a resource that is secured but has no
 * allowed roles declared must be DENIED, not granted. Before 3.0.1 this corner
 * granted access with only a warning — a resource you forgot to give roles was wide open.</p>
 */
class InMemoryAccessCheckInterceptorTest {

    private final InMemoryAccessCheckInterceptor interceptor = new InMemoryAccessCheckInterceptor();

    @BeforeEach
    @AfterEach
    void resetRules() {
        InMemoryAccessCheckInterceptor.clearAllRules();
    }

    @Test
    void securedResourceWithNoRolesIsDenied() {
        // No declared roles (resource never registered with the scanner) and no
        // programmatic grantAccess -> fail closed, even for a privileged-looking role.
        boolean granted = interceptor.hasPermission("UnconfiguredResource", Set.of("ADMIN"));

        assertThat(granted).isFalse();
    }

    @Test
    void matchingProgrammaticRoleIsAllowed() {
        InMemoryAccessCheckInterceptor.grantAccess("Reports", "ADMIN", "MANAGER");

        assertThat(interceptor.hasPermission("Reports", Set.of("MANAGER"))).isTrue();
    }

    @Test
    void nonMatchingRoleIsDenied() {
        InMemoryAccessCheckInterceptor.grantAccess("Reports", "ADMIN");

        // Roles ARE defined for the resource, but the user doesn't have any of them.
        assertThat(interceptor.hasPermission("Reports", Set.of("USER"))).isFalse();
    }

    @Test
    void emptyUserRolesAgainstConfiguredResourceIsDenied() {
        InMemoryAccessCheckInterceptor.grantAccess("Reports", "ADMIN");

        assertThat(interceptor.hasPermission("Reports", Set.of())).isFalse();
    }
}
