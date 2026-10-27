package com.dienmay.support;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.time.*;
import java.util.*;
import java.util.stream.*;

/** Chỉ đọc kết quả XML; không chạy test, không khởi tạo lớp hay container. */
public final class TestReportMain {
    public record Result(String className,String name,double seconds,String timestamp,boolean failed,boolean skipped,String reason) {
        String key(){return className+"#"+name.split("[\\[(]",2)[0];}
        String label(){return className+"#"+name;}
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args.length==0?".":args[0]).toAbsolutePath().normalize();
        var mapping=discover(root.resolve("target/test-classes"));
        Map<String,String> registry=catalog(root.resolve("docs/test-cases.md"));
        for(var ids:mapping.values())for(String id:ids)if(!registry.containsKey(id))throw new IllegalStateException("Mã ca chưa có trong bảng gốc: "+id);
        List<Result> results=new ArrayList<>();
        for(String folder:List.of("surefire-reports","failsafe-reports")){Path dir=root.resolve("target/"+folder);if(Files.isDirectory(dir))try(var files=Files.list(dir)){for(Path file:files.filter(p->p.getFileName().toString().startsWith("TEST-")&&p.toString().endsWith(".xml")).sorted().toList())results.addAll(parse(file));}}
        if(results.isEmpty())throw new IllegalStateException("Chưa có XML kết quả. Hãy chạy ./mvnw verify trước.");
        String text=TestingDocumentation.enrich(root,render(registry,mapping,results),registry,mapping,results);Path out=root.resolve("docs/test-results.md");Files.createDirectories(out.getParent());Files.writeString(out,text);Files.writeString(root.resolve("docs/tests-not-in-catalog.md"),renderExtensions(mapping,results));System.out.println(text.substring(0,text.indexOf("## Chi tiết")));
    }
    /** Danh mục Markdown do người dùng chốt là nguồn duy nhất; mã đã thay không còn là ca đang chạy. */
    static Map<String,String> catalog(Path file)throws java.io.IOException {
        Map<String,String> active=new TreeMap<>();Set<String> seen=new HashSet<>();
        for(String line:Files.readAllLines(file)){
            if(!line.matches("^\\|\\s*TC-[A-Z]+-[0-9]+\\s*\\|.*"))continue;
            String[] columns=line.split("\\|",-1);String id=columns[1].strip();
            if(columns.length<6||!id.matches("TC-[A-Z]+-[0-9]{2,}"))throw new IllegalStateException("Dòng ca không hợp lệ: "+line);
            if(!seen.add(id))throw new IllegalStateException("Mã ca bị lặp: "+id);
            if(!id.equals("TC-WEB-26"))active.put(id,columns[2].strip());
        }
        if(active.isEmpty())throw new IllegalStateException("Không tìm thấy ca đang dùng trong docs/test-cases.md.");
        return active;
    }
    static Map<String,List<String>> discover(Path classes)throws Exception{
        Map<String,List<String>> map=new HashMap<>();try(var files=Files.walk(classes)){for(Path file:files.filter(p->p.toString().endsWith(".class")).toList()){
            String name=classes.relativize(file).toString().replace(java.io.File.separatorChar,'.').replaceAll("\\.class$","");
            if(!name.startsWith("com.dienmay."))continue;
            Class<?> type=Class.forName(name,false,TestReportMain.class.getClassLoader());
            for(Method method:type.getDeclaredMethods()){
                boolean test=Arrays.stream(method.getAnnotations()).anyMatch(a->a.annotationType().getName().matches("org.junit.jupiter.api.(Test|RepeatedTest|TestFactory)|org.junit.jupiter.params.ParameterizedTest"));
                if(!test)continue;
                List<String> ids=Stream.concat(Arrays.stream(type.getAnnotationsByType(TestCase.class)),Arrays.stream(method.getAnnotationsByType(TestCase.class))).map(TestCase::value).distinct().toList();
                if(!ids.isEmpty())map.put(type.getName()+"#"+method.getName(),ids);
            }
        }}return map;
    }
    public static List<Result> parse(Path file)throws Exception{
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        Document doc=factory.newDocumentBuilder().parse(file.toFile());String timestamp=doc.getDocumentElement().getAttribute("timestamp");
        // Surefire có phiên bản không ghi timestamp. Khi đó dùng giờ ghi XML, không lấy giờ sinh báo cáo để giả làm giờ chạy test.
        if(timestamp.isBlank())timestamp=Files.getLastModifiedTime(file).toInstant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        List<Result> results=new ArrayList<>();NodeList cases=doc.getElementsByTagName("testcase");
        for(int n=0;n<cases.getLength();n++){Element e=(Element)cases.item(n);Element skipped=child(e,"skipped"),failure=child(e,"failure"),error=child(e,"error");results.add(new Result(e.getAttribute("classname"),e.getAttribute("name"),e.hasAttribute("time")?Double.parseDouble(e.getAttribute("time")):0,timestamp,failure!=null||error!=null,skipped!=null,skipped==null?"":skipped.getAttribute("message")));}return results;
    }
    private static Element child(Element node,String name){for(Node n=node.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&e.getTagName().equals(name))return e;return null;}
    public static String render(Map<String,String> registry,Map<String,List<String>> mapping,List<Result> results){
        Map<String,List<Result>> byCase=new TreeMap<>();registry.keySet().forEach(id->byCase.put(id,new ArrayList<>()));
        for(Result result:results)for(String id:mapping.getOrDefault(result.key(),List.of()))byCase.computeIfAbsent(id,k->new ArrayList<>()).add(result);
        long passed=byCase.values().stream().filter(v->!v.isEmpty()&&v.stream().noneMatch(r->r.failed||r.skipped)).count();
        long absent=byCase.values().stream().filter(List::isEmpty).count();long failed=byCase.size()-passed-absent;
        long skipped=results.stream().filter(Result::skipped).count(),testFailed=results.stream().filter(Result::failed).count(),unmapped=results.stream().filter(r->!mapping.containsKey(r.key())).count();
        StringBuilder out=new StringBuilder("# Kết quả kiểm thử\n\n## Tổng hợp\n\n");
        out.append("- Ngày sinh báo cáo: ").append(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))).append(" (Asia/Ho_Chi_Minh)\n");
        out.append("\n### Ca trong danh mục (TC-*)\n\n- Tổng số mã đang dùng: ").append(registry.size()).append("\n");
        out.append("- Số ca Đạt: ").append(passed).append("\n- Số ca Không đạt: ").append(failed).append("\n- Số ca Chưa có test: ").append(absent).append("\n- Tổng số test (lượt thực thi, gồm tham số hóa): ").append(results.size()).append("\n- Test hỏng/lỗi: ").append(testFailed).append("\n- Test bỏ qua: ").append(skipped).append("\n- Lượt test chưa ánh xạ mã ca: ").append(unmapped).append("\n");
        long taggedMethods=results.stream().filter(r->mapping.containsKey(r.key())).map(Result::key).distinct().count();
        long extraMethods=results.stream().filter(r->!mapping.containsKey(r.key())).map(Result::key).distinct().count();
        out.append("\n### Test có mã TC\n\n- Số method: ").append(taggedMethods).append("\n- Số lượt thực thi: ").append(results.size()-unmapped).append("\n\n### Test mở rộng (không mã TC)\n\n- Số method: ").append(extraMethods).append("\n- Số lượt thực thi: ").append(unmapped).append("\n\nMột lượt test có nhiều mã TC chỉ được đếm một lần trong tổng lượt thực thi. Test mở rộng được giữ theo quyết định của người dùng, không tự đặt mã; xem [phân nhóm](tests-not-in-catalog.md).\n");
        out.append("\nNgày chạy lấy timestamp XML; nếu XML không có thì lấy thời điểm ghi tệp XML, múi giờ Việt Nam. Danh mục đang dùng lấy từ docs/test-cases.md; TC-WEB-26 đã thay bằng TC-WEB-31–35, không tính vào tổng. Mã có trong danh mục nhưng không có test gắn mã được ghi Chưa có test. Giá trị dữ liệu mẫu có thể khác giữa các test biên tương đương; xem test-case-mapping.md để biết giới hạn. Một method tham số hóa gắn mã thì tất cả invocation của method đó cùng tham gia kết quả ca. Test bị bỏ qua không được tính Đạt. Thời gian bên dưới là tổng thời gian test XML; không phải tổng thời gian build. Báo cáo dùng XML lần chạy hiện có, không tự chạy lại test.\n\n## Chi tiết\n\n| ID | Mô tả | Có test tự động | Số test | Kết quả | Giây | Ngày chạy XML | Test liên quan |\n|---|---|---|---:|---|---:|---|---|\n");
        byCase.forEach((id,rows)->out.append("| ").append(id).append(" | ").append(safe(registry.getOrDefault(id,""))).append(" | ").append(rows.isEmpty()?"Chưa có test":"Đã có test").append(" | ").append(rows.size()).append(" | ").append(rows.isEmpty()?"Chưa có test":rows.stream().anyMatch(r->r.failed||r.skipped)?"Không đạt":"Đạt").append(" | ").append(String.format(Locale.ROOT,"%.3f",rows.stream().mapToDouble(Result::seconds).sum())).append(" | ").append(rows.stream().map(Result::timestamp).distinct().collect(Collectors.joining(", "))).append(" | ").append(rows.stream().map(Result::label).map(TestReportMain::safe).collect(Collectors.joining("<br>"))).append(" |\n"));
        out.append("\n## Test bỏ qua\n\n");if(skipped==0)out.append("Không có.\n");else results.stream().filter(Result::skipped).forEach(r->out.append("- ").append(safe(r.label())).append(": ").append(safe(r.reason.isBlank()?"THIẾU LÝ DO — cần bổ sung":r.reason)).append("\n"));
        out.append("\n## Test mở rộng đã chốt (không mã TC)\n\n");results.stream().filter(r->!mapping.containsKey(r.key())).map(Result::label).distinct().sorted().forEach(n->out.append("- ").append(safe(n)).append("\n"));return out.toString();
    }
    // Phân nhóm tài liệu, không suy đoán hoặc tạo mã TC. Nhóm nghiệp vụ cụ thể được ưu tiên.
    static String extensionGroup(String key){
        String type=key.substring(0,key.indexOf('#'));
        if(type.contains(".Stocktake"))return "Kiểm kê";
        if(type.contains(".Branch")||type.contains(".Supplier"))return "Chi nhánh / nhà cung cấp";
        if(type.endsWith(".ArchitectureTest"))return "Luật kiến trúc ArchUnit";
        String method=key.substring(key.indexOf('#')+1).toLowerCase(Locale.ROOT);
        if(method.matches(".*(concurrent|twostaff|vscomplete|betweenqueries|latehistoryfailure|failureafter|databasefailure|rollback|staleversion|uniqueconflict|oneorderfails).*"))return "Concurrency / chaos";
        return "Edge case khác";
    }
    static String renderExtensions(Map<String,List<String>> mapping,List<Result> results){
        List<String> groups=List.of("Kiểm kê","Chi nhánh / nhà cung cấp","Luật kiến trúc ArchUnit","Concurrency / chaos","Edge case khác");
        Map<String,List<Result>> methods=results.stream().filter(r->!mapping.containsKey(r.key())).collect(Collectors.groupingBy(Result::key,TreeMap::new,Collectors.toList()));
        StringBuilder out=new StringBuilder("# Test mở rộng không có mã TC\n\nTheo quyết định của người dùng: giữ các test này, không đặt mã mới và không dùng lại TC-WEB-26. Danh sách sinh từ XML của lần verify hiện có; tham số hóa/lặp chỉ tính một method nhưng giữ đủ số lượt thực thi.\n\n");
        out.append("Tổng: **").append(methods.size()).append(" method**, **").append(methods.values().stream().mapToInt(List::size).sum()).append(" lượt thực thi**. Con số 388 trong trao đổi trước là số liệu cũ, trước khi bổ sung test công cụ báo cáo.\n\n| Nhóm | Method | Lượt thực thi |\n|---|---:|---:|\n");
        for(String group:groups){var rows=methods.entrySet().stream().filter(e->extensionGroup(e.getKey()).equals(group)).toList();out.append("| ").append(group).append(" | ").append(rows.size()).append(" | ").append(rows.stream().mapToInt(e->e.getValue().size()).sum()).append(" |\n");}
        out.append("\nMỗi method thuộc đúng một nhóm. Kiểm kê, chi nhánh/nhà cung cấp và ArchUnit được ưu tiên; nhóm concurrency/chaos còn lại gồm test đồng thời, xung đột phiên bản, lỗi được tiêm và rollback. Nhóm cuối gồm ca biên nghiệp vụ, render, bảo mật và tự kiểm tra công cụ báo cáo/quy ước.\n");
        for(String group:groups){out.append("\n## ").append(group).append("\n\n");
            if(group.equals("Concurrency / chaos"))out.append("Hai test `OrderFulfillmentIT#change_twoStaffSameVersion_onlyOneWinsRepeated20Times` và `OrderFulfillmentIT#cancelStaff_vsComplete_onlyOneWinsAndNoInvoiceForCancelledRepeated20Times` thuộc nhóm **đồng thời trên cùng đơn**, không thuộc TC-WEB-31..35 và không dùng lại TC-WEB-26.\n\n");
            methods.forEach((key,rows)->{if(extensionGroup(key).equals(group)){String type=key.substring(0,key.indexOf('#')),name=key.substring(key.indexOf('#')+1);out.append("- [").append(type.substring(type.lastIndexOf('.')+1)).append("#").append(name).append("](../src/test/java/").append(type.replace('.','/')).append(".java): ").append(rows.size()).append(" lượt.\n");}});
        }return out.toString();
    }
    private static String safe(String value){return value.replace("|","\\|").replace("\r"," ").replace("\n"," ").replace("<","&lt;");}
}
