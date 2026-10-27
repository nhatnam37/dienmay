package com.dienmay.support;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TestConventionsTest {
    @Test @DisplayName("Tên test mô tả phương thức, điều kiện và kết quả; bỏ qua phải có lý do")
    void tests_namesAndDisabledAnnotations_explainIntent()throws Exception{
        Path root=Path.of("target/test-classes");List<String> violations=new ArrayList<>();
        try(var files=Files.walk(root)){for(Path p:files.filter(p->p.toString().endsWith(".class")).toList()){
            String name=root.relativize(p).toString().replace(java.io.File.separatorChar,'.').replaceAll("\\.class$","");if(!name.startsWith("com.dienmay."))continue;
            Class<?> type=Class.forName(name,false,getClass().getClassLoader());Disabled disabled=type.getAnnotation(Disabled.class);if(disabled!=null&&disabled.value().isBlank())violations.add(name+": @Disabled thiếu lý do");
            for(Method method:type.getDeclaredMethods())if(isTest(method)){
                if(!method.getName().matches("[a-zA-Z][a-zA-Z0-9]*_[a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)+"))violations.add(name+"#"+method.getName()+": cần method_condition_expectedResult");
                Disabled d=method.getAnnotation(Disabled.class);if(d!=null&&d.value().isBlank())violations.add(name+"#"+method.getName()+": @Disabled thiếu lý do");
            }
        }}assertThat(violations).as("Quy ước test giúp giải thích hành vi khi bảo vệ và không che test bằng @Disabled").isEmpty();
    }
    private static boolean isTest(Method method){return Arrays.stream(method.getAnnotations()).anyMatch(a->a.annotationType().getName().matches("org.junit.jupiter.api.(Test|RepeatedTest|TestFactory)|org.junit.jupiter.params.ParameterizedTest"));}
}
