package net.chrislehmann.contentproviderdao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created with IntelliJ IDEA.
 * User: clehmann
 * Date: 7/21/12
 * Time: 2:49 PM
 * To change this template use File | Settings | File Templates.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JoinedList  {
//    public String columnName();
    public boolean cascadeUpdates() default true;
    public String foreignKeyColumnName();
    public Class klass();
}