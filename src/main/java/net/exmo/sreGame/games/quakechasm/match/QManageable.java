package net.exmo.sreGame.games.quakechasm.match;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a Match field as adjustable via /quake match set at runtime. Ported from QManageable. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface QManageable {
    String name();
    int min() default Integer.MIN_VALUE;
    int max() default Integer.MAX_VALUE;
    String description() default "";
}
