package cn.xbmchina.annotation;


import java.lang.annotation.*;
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XXAutowired {
    String value() default "";
}