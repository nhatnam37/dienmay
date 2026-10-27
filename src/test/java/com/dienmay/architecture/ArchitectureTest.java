package com.dienmay.architecture;

import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.assertj.core.api.Assertions.*;

class ArchitectureTest {
    static final JavaClasses CODE = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.dienmay");
    @Test @DisplayName("Controller không truy cập repository: nghiệp vụ phải đi qua service")
    void controllers_repositoryDependency_forbidden(){noClasses().that().haveSimpleNameEndingWith("Controller").should().dependOnClassesThat().resideInAnyPackage("..repository..").because("Controller phải gọi service để không bỏ qua giao dịch và phân quyền").check(CODE);}
    @Test @DisplayName("Chỉ máy trạng thái được thay đổi trạng thái Serial")
    void serialStatus_mutations_onlyStateService(){
        var violations=new ArrayList<String>();
        for(var c:CODE)for(var call:c.getMethodCallsFromSelf())if(call.getTarget().getOwner().getName().equals("com.dienmay.inventory.serial.SerialItem")&&call.getTarget().getName().equals("setStatus")&&!c.getName().equals("com.dienmay.inventory.serial.SerialStateService"))violations.add(call.getDescription());
        assertThat(violations).as("Chỉ SerialStateService được đổi trạng thái Serial để luôn ghi lịch sử và kiểm tra luồng").isEmpty();
    }
    @Test @DisplayName("Catalog khai báo cổng, inventory cài đặt; catalog không biết inventory")
    void catalog_inventoryDependency_forbidden(){
        // Inventory được phép dùng catalog và cài interface của catalog; cấm chiều ngược để tránh vòng phụ thuộc.
        noClasses().that().resideInAPackage("..catalog..").should().dependOnClassesThat().resideInAPackage("..inventory..").because("Catalog phải khai báo interface, inventory cài đặt để không phụ thuộc vòng").check(CODE);
    }
    @Test @DisplayName("Entity không được xuất hiện trong chữ ký controller kể cả kiểu generic")
    void controllers_entitySignatures_forbidden(){var errors=new ArrayList<String>();for(var c:CODE)if(c.getSimpleName().endsWith("Controller"))for(var method:c.reflect().getDeclaredMethods()){
        if(hasEntity(method.getGenericReturnType(),new HashSet<>()))errors.add(method+" trả entity");
        for(Type type:method.getGenericParameterTypes())if(hasEntity(type,new HashSet<>()))errors.add(method+" nhận entity");
    }assertThat(errors).as("Controller phải dùng DTO, không làm lộ entity/lazy loading ra giao diện").isEmpty();}
    static boolean hasEntity(Type type,Set<Type> seen){if(!seen.add(type))return false;if(type instanceof Class<?> c)return c.isAnnotationPresent(jakarta.persistence.Entity.class)||(c.isArray()&&hasEntity(c.getComponentType(),seen));if(type instanceof ParameterizedType p)return hasEntity(p.getRawType(),seen)||Arrays.stream(p.getActualTypeArguments()).anyMatch(t->hasEntity(t,seen));if(type instanceof WildcardType w)return Arrays.stream(w.getUpperBounds()).anyMatch(t->hasEntity(t,seen));if(type instanceof GenericArrayType a)return hasEntity(a.getGenericComponentType(),seen);return false;}
    @Test @DisplayName("Không dùng Lombok để sinh code ngầm")
    void production_lombokDependencies_forbidden(){noClasses().should().dependOnClassesThat().resideInAPackage("lombok..").because("Không dùng Lombok để sinh viên đọc và giải thích toàn bộ code").check(CODE);assertThat(read(Path.of("pom.xml"))).as("pom.xml không được khai báo Lombok").doesNotContain("<artifactId>lombok</artifactId>");}
    @Test @DisplayName("Template không render HTML không escape: phòng chống XSS")
    void templates_unescapedOutput_forbidden()throws Exception{
        // th:utext bỏ escape; dữ liệu sản phẩm/khách có thể chứa script nên chỉ cho th:text.
        try(var files=Files.walk(Path.of("src/main/resources/templates"))){for(Path file:files.filter(p->p.toString().endsWith(".html")).toList())assertThat(read(file)).as("Không dùng th:utext/data-th-utext để chống XSS: %s",file).doesNotMatch("(?is).*(?:th:utext|data-th-utext)\\s*=.*");}
    }
    @Test @DisplayName("Các hàm public ghi DB của service phải có giao dịch ghi")
    void services_databaseWrites_transactionRequired(){var errors=new ArrayList<String>();for(var c:CODE)if(c.isAnnotatedWith(org.springframework.stereotype.Service.class))for(var m:c.getMethods())if(m.getModifiers().contains(JavaModifier.PUBLIC)&&writes(m,new HashSet<>())){
        Method method=m.reflect();var tx=AnnotatedElementUtils.findMergedAnnotation(method,Transactional.class);if(tx==null)tx=AnnotatedElementUtils.findMergedAnnotation(c.reflect(),Transactional.class);if(tx==null||tx.readOnly())errors.add(m.getFullName());
    }assertThat(errors).as("Service ghi DB phải có @Transactional(readOnly=false) để rollback toàn bộ khi lỗi").isEmpty();}
    static boolean writes(JavaMethod method,Set<JavaMethod> seen){if(!seen.add(method))return false;for(var call:method.getMethodCallsFromSelf()){
        var target=call.getTarget();String owner=target.getOwner().getName(),name=target.getName();
        if((owner.contains(".repository.")&&name.matches("save.*|delete.*|insert.*|update.*"))||(owner.startsWith("org.springframework.jdbc.")&&name.matches("update|batchUpdate|execute")))return true;
        if(target.getOwner().equals(method.getOwner()))for(var own:method.getOwner().getMethods())if(own.getFullName().equals(target.getFullName())&&writes(own,seen))return true;
    }return false;}
    static String read(Path file){try{return Files.readString(file);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
}
