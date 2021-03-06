package net.chrislehmann.contentproviderdao;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created with IntelliJ IDEA.
 * User: clehmann
 * Date: 7/9/12
 * Time: 6:56 PM
 * To change this template use File | Settings | File Templates.
 */

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Field {
    public String columnName();

    public boolean id() default false;
}
