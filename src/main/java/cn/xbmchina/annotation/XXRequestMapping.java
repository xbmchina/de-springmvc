package cn.xbmchina.annotation;


import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XXRequestMapping {
    String value() default "";
}