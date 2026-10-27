package com.dienmay.support;
import java.lang.annotation.*;
import org.junit.jupiter.api.Tag;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD,ElementType.TYPE})
@Tag("test-case")
public @interface TestCases { TestCase[] value(); }
