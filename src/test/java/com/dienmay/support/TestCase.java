package com.dienmay.support;
import java.lang.annotation.*;
import org.junit.jupiter.api.Tag;
/** ID lấy từ bảng ca kiểm thử đã được xác nhận, không sinh/đoán từ tên method. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD,ElementType.TYPE})
@Repeatable(TestCases.class)
@Tag("test-case")
public @interface TestCase { String value(); }
