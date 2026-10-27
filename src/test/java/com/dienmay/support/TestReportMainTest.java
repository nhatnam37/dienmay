package com.dienmay.support;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TestReportMainTest {
    @TempDir Path temp;
    @Test @DisplayName("Báo cáo gom nhiều test cùng ca, giữ ca chưa tự động và lý do bỏ qua")
    void render_mixedResults_reportsFailureMissingAndSkipped(){var rows=List.of(new TestReportMain.Result("C","test_ok",0.1,"2026-10-21",false,false,""),new TestReportMain.Result("C","test_fail[1]",0.2,"2026-10-21",true,false,""),new TestReportMain.Result("C","test_skip",0,"2026-10-21",false,true,"Chờ thiết bị thật"));var text=TestReportMain.render(Map.of("TC-X-01","Ca một","TC-X-02","Ca thiếu"),Map.of("C#test_ok",List.of("TC-X-01"),"C#test_fail",List.of("TC-X-01")),rows);assertThat(text).contains("Số ca Không đạt: 1","Số ca Chưa có test: 1","Tổng số test (lượt thực thi, gồm tham số hóa): 3","Test bỏ qua: 1","Chờ thiết bị thật","0.300");}
    @Test @DisplayName("Một test có thể phủ nhiều mã ca đã xác nhận")
    void render_multipleIds_countsOneExecutionPerCase(){var result=new TestReportMain.Result("C","test_ok",0.5,"2026-10-21",false,false,"");var text=TestReportMain.render(Map.of("TC-X-01","Một","TC-X-02","Hai"),Map.of("C#test_ok",List.of("TC-X-01","TC-X-02")),List.of(result));assertThat(text).contains("Số ca Đạt: 2","### Test có mã TC\n\n- Số method: 1\n- Số lượt thực thi: 1","Tổng số test (lượt thực thi, gồm tham số hóa): 1");}
    @Test @DisplayName("XML kết quả đọc riêng failure, error và skipped")
    void parse_failureErrorAndSkip_preservesStatusesAndReasons()throws Exception{var xml=temp.resolve("TEST-demo.xml");Files.writeString(xml,"""
        <testsuite timestamp="2026-10-21T09:00:00"><testcase classname="C" name="a" time="0.2"/><testcase classname="C" name="b"><failure message="sai"/></testcase><testcase classname="C" name="c"><error/></testcase><testcase classname="C" name="d"><skipped message="Chờ thiết bị"/></testcase></testsuite>
        """);var rows=TestReportMain.parse(xml);assertThat(rows).hasSize(4);assertThat(rows.stream().filter(TestReportMain.Result::failed)).hasSize(2);assertThat(rows.get(3).reason()).isEqualTo("Chờ thiết bị");assertThat(rows.get(0).seconds()).isEqualTo(0.2);
        var noTimestamp=temp.resolve("TEST-no-timestamp.xml");Files.writeString(noTimestamp,"<testsuite><testcase classname='C' name='a'/></testsuite>");Files.setLastModifiedTime(noTimestamp,java.nio.file.attribute.FileTime.from(java.time.Instant.parse("2026-10-21T03:00:00Z")));assertThat(TestReportMain.parse(noTimestamp).getFirst().timestamp()).isEqualTo("2026-10-21T10:00:00+07:00");}
    @Test @DisplayName("Từ chối XML có thực thể ngoài, không đọc dữ liệu máy")
    void parse_externalEntity_rejectsDocument()throws Exception{var xml=temp.resolve("evil.xml");Files.writeString(xml,"<!DOCTYPE testsuite [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><testsuite>&x;</testsuite>");assertThatThrownBy(()->TestReportMain.parse(xml)).isInstanceOf(org.xml.sax.SAXParseException.class);}
    @Test @DisplayName("Annotation mã ca lặp được và là meta-annotation của Tag")
    void annotation_repeatable_preservesBothIds()throws Exception{var annotations=getClass().getDeclaredMethod("fixture").getAnnotationsByType(TestCase.class);assertThat(annotations).extracting(TestCase::value).containsExactly("TC-X-01","TC-X-02");assertThat(TestCase.class.isAnnotationPresent(org.junit.jupiter.api.Tag.class)).isTrue();}
    @TestCase("TC-X-01") @TestCase("TC-X-02") private void fixture(){} // Fixture annotation, không phải ca kiểm thử thực thi.

    @Test @DisplayName("Danh mục người dùng có 162 ca đang dùng, không đếm mã WEB-26 đã thay")
    void catalog_authoritativeMarkdown_excludesRetiredId()throws Exception{
        var cases=TestReportMain.catalog(Path.of("docs/test-cases.md"));assertThat(cases).hasSize(162).containsKeys("TC-AUTH-04","TC-WEB-31","TC-WEB-35","TC-WEB-36","TC-WEB-37").doesNotContainKey("TC-WEB-26");
        var mapping=TestReportMain.discover(Path.of("target/test-classes"));assertThat(mapping.get("com.dienmay.config.SecurityRulesTest#staff_whenAnonymous_redirectsToEmployeeLogin")).containsExactly("TC-AUTH-04");assertThat(mapping.values().stream().flatMap(List::stream)).allMatch(cases::containsKey);
    }
    @Test @DisplayName("Từ chối danh mục lặp mã và không biến ca chưa có test thành Đạt")
    void catalog_duplicateOrUnmappedCase_rejectsDuplicateAndReportsMissing()throws Exception{
        Path file=temp.resolve("cases.md");Files.writeString(file,"| TC-AUTH-01 | Một | dữ liệu | kết quả |\n| TC-AUTH-01 | Hai | dữ liệu | kết quả |\n");assertThatThrownBy(()->TestReportMain.catalog(file)).hasMessageContaining("Mã ca bị lặp");
        Files.writeString(file,"| TC-WEB-26 | đã thay | x | Không dùng |\n| TC-AUTH-07 | Sai mật khẩu | admin | login?error |\n");var cases=TestReportMain.catalog(file);var report=TestReportMain.render(cases,Map.of(),List.of(new TestReportMain.Result("C","test_extra",0,"2026-10-21",false,false,"")));assertThat(report).contains("Số ca Chưa có test: 1","| TC-AUTH-07 | Sai mật khẩu | Chưa có test | 0 | Chưa có test |").doesNotContain("| TC-WEB-26 |");
    }
    @Test @DisplayName("Test mở rộng được nhóm một lần, tách số method và số lượt")
    void renderExtensions_parameterizedAndConcurrent_groupsWithoutInventingIds(){
        var rows=List.of(new TestReportMain.Result("com.dienmay.inventory.StocktakeServiceTest","compare_case[1]",0,"",false,false,""),new TestReportMain.Result("com.dienmay.inventory.StocktakeServiceTest","compare_case[2]",0,"",false,false,""),new TestReportMain.Result("com.dienmay.orders.OrderFulfillmentIT","change_twoStaffSameVersion_onlyOneWinsRepeated20Times",0,"",false,false,""),new TestReportMain.Result("C","mapped",0,"",false,false,""));
        var text=TestReportMain.renderExtensions(Map.of("C#mapped",List.of("TC-X-01")),rows);
        assertThat(text).contains("**2 method**, **3 lượt thực thi**","| Kiểm kê | 1 | 2 |","| Concurrency / chaos | 1 | 1 |","đồng thời trên cùng đơn","không thuộc TC-WEB-31..35").doesNotContain("C#mapped");
        assertThat(TestReportMain.extensionGroup("com.dienmay.organization.BranchWorkflowIT#create_concurrent_failure")).isEqualTo("Chi nhánh / nhà cung cấp");
        assertThat(TestReportMain.extensionGroup("com.dienmay.architecture.ArchitectureTest#controllers_rule")).isEqualTo("Luật kiến trúc ArchUnit");
    }
}
