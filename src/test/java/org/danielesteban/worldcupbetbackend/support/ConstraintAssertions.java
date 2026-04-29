package org.danielesteban.worldcupbetbackend.support;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AssertJ helpers tailored to database constraint violations.
 * <p>
 * Integration tests that bypass Spring's repository layer (for example,
 * using {@code TestEntityManager#persistAndFlush} or
 * {@code createNativeQuery().executeUpdate()}) do not receive the translated
 * {@code DataIntegrityViolationException}; Hibernate's
 * {@code ConstraintViolationException} reaches the test verbatim. Asserting
 * by constraint name is therefore the stable, implementation-agnostic way to
 * verify that the expected database constraint actually fired.
 */
public final class ConstraintAssertions {

    private ConstraintAssertions() {
        // static helpers only
    }

    /**
     * Asserts that the given callable throws some exception whose stack
     * (either its message or the message of any cause) mentions the named
     * database constraint. This is robust to whichever layer ({@code JPA},
     * {@code Hibernate}, {@code Spring}) happens to wrap the original
     * {@code SQLException}.
     */
    public static AbstractThrowableAssert<?, ? extends Throwable> assertViolates(
            String constraintName, ThrowingCallable callable) {
        return assertThatThrownBy(callable)
                .satisfies(t -> {
                    Throwable current = t;
                    while (current != null) {
                        String message = current.getMessage();
                        if (message != null && message.contains(constraintName)) {
                            return;
                        }
                        current = current.getCause();
                    }
                    throw new AssertionError(
                            "Expected a throwable whose message chain mentions '"
                                    + constraintName + "' but got: " + t);
                });
    }
}
