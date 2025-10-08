package ma.youcode.surevote.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit logging via AOP.
 * Any method annotated with @Auditable will have its execution
 * intercepted and logged in the LogAudit table.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * The type/category of the action being audited.
     * Examples: "LOGIN", "VOTE_SUBMIT", "ELECTION_CREATED", "USER_DEACTIVATED"
     */
    String actionType() default "";

    /**
     * Human-readable description of the action.
     */
    String description() default "";
}
