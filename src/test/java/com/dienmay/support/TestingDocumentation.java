package com.dienmay.support;

import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Sinh phần dẫn xuất; không suy đoán yêu cầu hoặc biến ca thiếu thành ca đạt. */
final class TestingDocumentation {
    private TestingDocumentation() {}
    static String enrich(Path root, String report, Map<String,String> catalog,
                         Map<String,List<String>> mapping, List<TestReportMain.Result> results,
                         TestReportMain.RunResults runs,PerformanceEvidence performance) throws Exception {
        Properties run=new Properties();
        Path metadata=root.resolve("docs/evidence/automated/latest-run.properties");
        if(Files.exists(metadata))try(var input=Files.newInputStream(metadata)){run.load(input);}
        // Không gắn metadata lượt cũ vào XML mới khi người dùng vừa chạy test ngoài script bằng chứng.
        if(!matchesEvidence(root,run))run.clear();
        StringBuilder header=new StringBuilder("## Thông tin lần chạy verify mặc định\n\n");
        for(String key:List.of("started","finished","commit","dirty","command","exitCode","elapsedSeconds","evidence"))
            header.append("- ").append(key).append(": ").append(run.getProperty(key,"chưa ghi nhận")).append("\n");
        header.append("\nMetadata trên chỉ thuộc bộ mặc định (Surefire + Failsafe), không thuộc lần chạy performance riêng. Commit là mốc HEAD; dirty=true nghĩa là kết quả áp dụng cho working tree có sửa đổi, không phải riêng commit đó. Không cộng thời gian từng test để suy ra thời gian build.\n\n");
        if(run.getProperty("format","").equals("hashes-v1"))header.append("Bằng chứng mặc định dùng manifest SHA-256 + metadata; XML thô ở target/ không được chép vào repository. Hash của toàn bộ XML và JaCoCo hiện có đã được đối chiếu trước khi gắn metadata.\n\n");
        else if(!run.isEmpty())header.append("Bằng chứng mặc định dùng bản chụp XML và JaCoCo đã đối chiếu byte.\n\n");
        header.append(coverageSummary(root,!run.isEmpty()));
        header.append(renderRunScopes(runs,mapping,performance));
        StringBuilder modules=new StringBuilder("## Tổng hợp theo module\n\nĐếm lượt thực thi theo package của lớp test; đây không phải số use case. Một test chỉ nằm trong một module.\n\n| Module | Lượt | Hỏng/lỗi | Bỏ qua |\n|---|---:|---:|---:|\n");
        var groups=results.stream().collect(Collectors.groupingBy(r->module(r.className()),TreeMap::new,Collectors.toList()));
        groups.forEach((name,rows)->modules.append("| ").append(name).append(" | ").append(rows.size()).append(" | ").append(rows.stream().filter(TestReportMain.Result::failed).count()).append(" | ").append(rows.stream().filter(TestReportMain.Result::skipped).count()).append(" |\n"));
        Set<String> executed=results.stream().flatMap(r->mapping.getOrDefault(r.key(),List.of()).stream()).collect(Collectors.toSet());
        modules.append("\n## Các ca không tự động hóa\n\nCác mã dưới đây chưa có invocation gắn mã trong XML. Điều này không khẳng định chức năng chưa tồn tại; không thay bằng kết quả thủ công chưa chạy.\n\n");
        catalog.forEach((id,description)->{if(!executed.contains(id))modules.append("- ").append(id).append(": ").append(description).append(" — chưa có test gắn mã; xem manual-tests.md.\n");});
        modules.append("\nCác trường kết quả thủ công chưa có bằng chứng vẫn để trống; không dùng kết quả tự động để điền thay kiểm thử trình duyệt, in giấy hay demo hai người mua. Hiệu năng HTTP HTML + API + tài nguyên nội bộ, nếu đã đo, được ghi riêng trong phần performance ở đầu báo cáo; chưa đo CDN, JavaScript thực thi hay dựng layout trong trình duyệt. Mẫu ghi kết quả và ảnh ở [manual-tests.md](manual-tests.md).\n\n");
        generateTraceability(root,catalog,mapping,executed);
        return report.replace("## Tổng hợp\n",header+"## Tổng hợp\n").replace("## Chi tiết\n",modules+"## Chi tiết\n");
    }
    record PerformanceEvidence(boolean verified,String reason,String artifact,Properties metadata) {}

    /** Chỉ nhập số đo khi cùng bộ XML, artifact và khoảng thời gian đều khớp manifest riêng. */
    static PerformanceEvidence performanceEvidence(Path root,List<TestReportMain.Result> results)throws Exception {
        if(results.isEmpty())return new PerformanceEvidence(false,"Chưa thực hiện: không có XML trong target/performance-reports.","",new Properties());
        Path artifact=root.resolve("target/performance-results/report-pages.md"),manifest=root.resolve("target/performance-results/run.properties");
        if(!Files.isRegularFile(artifact)||!Files.isRegularFile(manifest))return unverifiedPerformance("Thiếu artifact report-pages.md hoặc manifest run.properties riêng.");
        Properties run=new Properties();try(var in=Files.newInputStream(manifest)){run.load(in);}
        if(!sha256(artifact).equals(run.getProperty("artifactSha256")))return unverifiedPerformance("Hash artifact không khớp manifest; có thể là số đo của lượt cũ.");
        List<Path> xml=xmlFiles(root.resolve("target/performance-reports"));
        if(xml.isEmpty())return unverifiedPerformance("Không có XML performance để đối chiếu manifest.");
        Set<String> expected=xml.stream().map(p->"xml."+p.getFileName()+".sha256").collect(Collectors.toSet());
        Set<String> recorded=run.stringPropertyNames().stream().filter(k->k.startsWith("xml.")&&k.endsWith(".sha256")).collect(Collectors.toSet());
        if(!expected.equals(recorded))return unverifiedPerformance("Danh sách XML performance không khớp manifest.");
        for(Path file:xml)if(!sha256(file).equals(run.getProperty("xml."+file.getFileName()+".sha256")))return unverifiedPerformance("Hash XML performance không khớp manifest; không gộp kết quả cũ.");
        try {
            Instant start=Instant.parse(run.getProperty("started","")),finish=Instant.parse(run.getProperty("finished",""));
            if(finish.isBefore(start))return unverifiedPerformance("Khoảng thời gian performance không hợp lệ.");
            // Cho phép sai khác dưới một giây do manifest shell ghi timestamp đến giây.
            List<Path> files=new ArrayList<>(xml);files.add(artifact);
            for(Path file:files){Instant time=Files.getLastModifiedTime(file).toInstant();if(time.isBefore(start.minusSeconds(1))||time.isAfter(finish.plusSeconds(1)))return unverifiedPerformance("XML hoặc artifact nằm ngoài khoảng thời gian performance; không dùng bằng chứng stale.");}
        } catch(java.time.format.DateTimeParseException invalid){return unverifiedPerformance("Manifest performance thiếu started/finished ISO-8601 hợp lệ.");}
        if(run.getProperty("command","").isBlank()||run.getProperty("exitCode","").isBlank())return unverifiedPerformance("Manifest performance thiếu lệnh chạy hoặc exitCode.");
        return new PerformanceEvidence(true,"Hash XML/artifact và khoảng thời gian khớp manifest performance riêng.",Files.readString(artifact),run);
    }
    private static PerformanceEvidence unverifiedPerformance(String reason){return new PerformanceEvidence(false,"Chưa xác minh: "+reason,"",new Properties());}
    static String renderRunScopes(TestReportMain.RunResults runs,Map<String,List<String>> mapping,PerformanceEvidence performance) {
        StringBuilder out=new StringBuilder("## Phạm vi từng bộ test\n\nTổng và bảng TC phía dưới gộp các lượt mặc định với performance đã xác minh; đây là hai lệnh chạy riêng, không phải một lượt verify mặc định.\n\n| Bộ test | Lượt XML | Có mã TC | Mở rộng | Hỏng/lỗi | Bỏ qua | Gộp vào bảng TC |\n|---|---:|---:|---:|---:|---:|---|\n");
        appendScope(out,"Mặc định: Surefire + Failsafe",runs.standardResults(),mapping,true);
        appendScope(out,"Performance: profile perf",runs.performanceResults(),mapping,performance.verified());
        out.append("\n## Performance — lần chạy riêng\n\n").append(performance.reason()).append("\n\n");
        if(!performance.verified())return out.append("Các invocation performance chưa xác minh không được tính Đạt hoặc gộp vào tổng TC. Chạy ./mvnw -Pperf verify để tạo XML, artifact và manifest tự động của chính lượt đó; không lấy metadata mặc định thay thế.\n\n").toString();
        Properties run=performance.metadata();
        for(String key:List.of("started","finished","commit","dirty","command","commandKind","exitCode","exitCodeScope","metadataOrigin","elapsedSeconds","evidence"))out.append("- ").append(key).append(": ").append(run.getProperty(key,"chưa ghi nhận")).append("\n");
        out.append("- artifact SHA-256: ").append(run.getProperty("artifactSha256")).append("\n- Ngày XML performance: ").append(runs.performanceResults().stream().map(TestReportMain.Result::timestamp).distinct().collect(Collectors.joining(", "))).append("\n\nSố đo dưới đây đọc nguyên văn từ target/performance-results/report-pages.md đã khớp manifest: HTTP HTML + API + tài nguyên nội bộ trên môi trường nêu trong artifact, không suy ra từ thời gian query hoặc thời gian build. Chưa đo tải CDN, thực thi JavaScript, dựng layout hay cảm nhận tốc độ trong trình duyệt.\n\n");
        return out.append(performance.artifact()).append("\n\n").toString();
    }
    private static void appendScope(StringBuilder out,String name,List<TestReportMain.Result> rows,Map<String,List<String>> mapping,boolean accepted){
        long tagged=rows.stream().filter(r->mapping.containsKey(r.key())).count();
        out.append("| ").append(name).append(" | ").append(rows.size()).append(" | ").append(tagged).append(" | ").append(rows.size()-tagged).append(" | ").append(rows.stream().filter(TestReportMain.Result::failed).count()).append(" | ").append(rows.stream().filter(TestReportMain.Result::skipped).count()).append(" | ").append(accepted?"Có":"Không").append(" |\n");
    }
    static boolean matchesEvidence(Path root,Properties run)throws java.io.IOException {
        String path=run.getProperty("evidence","");if(path.isBlank())return false;
        Path saved=root.resolve(path).normalize();if(!saved.startsWith(root.normalize()))return false;
        if(run.getProperty("format","").equals("hashes-v1"))return matchesHashEvidence(root,run);
        for(String folder:List.of("surefire-reports","failsafe-reports")){
            Path current=root.resolve("target/"+folder),snapshot=saved.resolve(folder);
            if(!Files.isDirectory(current)||!Files.isDirectory(snapshot))return false;
            List<String> names;
            try(var files=Files.list(current)){names=files.map(p->p.getFileName().toString()).filter(n->n.startsWith("TEST-")&&n.endsWith(".xml")).sorted().toList();}
            List<String> stored;
            try(var files=Files.list(snapshot)){stored=files.map(p->p.getFileName().toString()).filter(n->n.startsWith("TEST-")&&n.endsWith(".xml")).sorted().toList();}
            if(!names.equals(stored)||names.isEmpty())return false;
            for(String name:names)if(Files.mismatch(current.resolve(name),snapshot.resolve(name))!=-1)return false;
        }
        Path coverage=root.resolve("target/site/jacoco/jacoco.xml"),snapshot=saved.resolve("jacoco.xml");
        return Files.isRegularFile(coverage)&&Files.isRegularFile(snapshot)&&Files.mismatch(coverage,snapshot)==-1;
    }
    private static boolean matchesHashEvidence(Path root,Properties run)throws java.io.IOException {
        Set<String> expected=new HashSet<>();
        for(String folder:List.of("surefire-reports","failsafe-reports")){
            List<Path> files=xmlFiles(root.resolve("target/"+folder));if(files.isEmpty())return false;
            for(Path file:files){String key="sha256."+folder+"/"+file.getFileName();expected.add(key);if(!sha256(file).equals(run.getProperty(key)))return false;}
        }
        Path coverage=root.resolve("target/site/jacoco/jacoco.xml");expected.add("sha256.jacoco.xml");
        Set<String> recorded=run.stringPropertyNames().stream().filter(k->k.startsWith("sha256.")).collect(Collectors.toSet());
        return expected.equals(recorded)&&Files.isRegularFile(coverage)&&sha256(coverage).equals(run.getProperty("sha256.jacoco.xml"));
    }
    private static List<Path> xmlFiles(Path dir)throws java.io.IOException {
        if(!Files.isDirectory(dir))return List.of();
        try(var files=Files.list(dir)){return files.filter(p->p.getFileName().toString().startsWith("TEST-")&&p.toString().endsWith(".xml")).sorted().toList();}
    }
    static String sha256(Path file)throws java.io.IOException {
        try(var in=Files.newInputStream(file)){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static String coverageSummary(Path root,boolean evidenceVerified)throws Exception {
        if(!evidenceVerified)return "### JaCoCo của bộ mặc định\n\nChưa xác minh: không gắn độ phủ từ artifact chưa khớp bằng chứng mặc định.\n\n";
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var document=factory.newDocumentBuilder().parse(root.resolve("target/site/jacoco/jacoco.xml").toFile());
        StringBuilder out=new StringBuilder("### JaCoCo của bộ mặc định\n\nNguồn: root counter trong target/site/jacoco/jacoco.xml đã khớp bằng chứng. Phạm vi loại trừ và cổng chất lượng theo pom.xml; tỷ lệ tổng không thay thế cổng theo service/lớp lõi.\n\n| Chỉ số | Đã phủ | Tổng | Tỷ lệ |\n|---|---:|---:|---:|\n");
        for(var node=document.getDocumentElement().getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof org.w3c.dom.Element e&&e.getTagName().equals("counter")&&List.of("LINE","BRANCH").contains(e.getAttribute("type"))){
            long covered=Long.parseLong(e.getAttribute("covered")),total=covered+Long.parseLong(e.getAttribute("missed"));
            out.append("| ").append(e.getAttribute("type")).append(" | ").append(covered).append(" | ").append(total).append(" | ").append(total==0?"chưa có dữ liệu":String.format(Locale.ROOT,"%.2f%%",100.0*covered/total)).append(" |\n");
        }
        return out.append("\n").toString();
    }
    static String module(String className){String[] parts=className.split("\\.");return parts.length>3?parts[2]:"application";}
    static void generateTraceability(Path root,Map<String,String> catalog,Map<String,List<String>> mapping,Set<String> executed)throws Exception{
        var rows=Files.readAllLines(root.resolve("docs/requirements-test-map.tsv"));
        Set<String> seen=new HashSet<>();List<String> gaps=new ArrayList<>();
        StringBuilder out=new StringBuilder("# Ma trận truy vết — DỰ THẢO CHỜ DUYỆT\n\nSinh bởi TestingDocumentation từ `requirements-test-map.tsv`, annotation @TestCase đã biên dịch và XML hiện có. Nguồn khảo sát ở requirements-sources.md: 8 mã có định nghĩa, 48 mã THIẾU ĐỊNH NGHĨA; không gán thứ tự theo suy đoán. Người dùng cần cung cấp/duyệt định nghĩa và ánh xạ trước khi dùng làm ma trận chính thức.\n\n| Yêu cầu | Định nghĩa / nguồn | TC đề xuất | Cách kiểm / thực trạng | Trạng thái định nghĩa |\n|---|---|---|---|---|\n");
        for(String row:rows){if(row.isBlank()||row.startsWith("#"))continue;String[] c=row.split("\t",-1);if(c.length!=5||!c[0].matches("UC[0-9]{2}|FR-[0-9]{2}|NFR-[0-9]{2}")||!seen.add(c[0]))throw new IllegalStateException("Ánh xạ không hợp lệ: "+row);
            List<String> ids=c[2].isBlank()?List.of():List.of(c[2].split(","));
            for(String id:ids)if(!catalog.containsKey(id))throw new IllegalStateException("TC không hoạt động: "+id);
            String status=ids.isEmpty()?"Chưa có ánh xạ TC; xem bằng chứng kỹ thuật nếu có":ids.stream().map(id->id+": "+(executed.contains(id)?"có test tự động":"chưa có test tự động")).collect(Collectors.joining("; "));
            out.append("| ").append(c[0]).append(" | ").append(c[1]).append(" | ").append(c[2]).append(" | ").append(c[3]).append("; ").append(status).append(" | ").append(c[4]).append(" |\n");
            if(c[1].equals("THIẾU ĐỊNH NGHĨA"))gaps.add(c[0]);
        }
        Set<String> required=new HashSet<>();for(int i=1;i<=24;i++){required.add("UC%02d".formatted(i));required.add("FR-%02d".formatted(i));}for(int i=1;i<=8;i++)required.add("NFR-%02d".formatted(i));
        if(!seen.equals(required))throw new IllegalStateException("Ma trận phải có đúng UC01–24, FR-01–24, NFR-01–08");
        out.append("\n## Khoảng trống định nghĩa / ánh xạ\n\n").append(gaps.size()).append(" mã chưa thể đối chiếu: ").append(String.join(", ",gaps)).append(". Đây là khoảng trống tài liệu, chưa đủ cơ sở kết luận các chức năng đó thiếu test.\n\n## Khoảng trống ca tự động đã xác định\n\n");
        catalog.forEach((id,description)->{if(!executed.contains(id))out.append("- ").append(id).append(": ").append(description).append(". Đề xuất bổ sung test xác định đúng dữ liệu/đường dẫn của ca; NFR-04 cần đo HTTP toàn trang trên bộ perf.\n");});
        out.append("\n## Cách duyệt và cập nhật\n\n1. Chủ đồ án cung cấp tên và định nghĩa từng UC/FR/NFR.\n2. Sửa cột định nghĩa, TC và cách kiểm trong tệp TSV; đối chiếu assertion, không chỉ tên test.\n3. Chủ đồ án duyệt từng dòng (cột cuối). Trạng thái định nghĩa không phải phê duyệt ánh xạ; mọi ánh xạ hiện chờ chủ đồ án duyệt.\n4. Chạy lại `./mvnw exec:java@test-report`. Công cụ từ chối TC lạ/đã bỏ, mã yêu cầu trùng hoặc thiếu; kiểm tra thủ công vẫn cần bằng chứng riêng.\n\nTC không có trong ánh xạ yêu cầu không đồng nghĩa không chạy; toàn bộ mã và test mở rộng vẫn nằm trong test-results.md. Ma trận dự thảo chưa chứng minh bao phủ yêu cầu 100%.\n");
        Files.writeString(root.resolve("docs/traceability-matrix.md"),out);
        generateJoined(root, catalog, executed);
    }
    private static void generateJoined(Path root,Map<String,String> catalog,Set<String> executed)throws Exception {
        List<String[]> actual=Files.readAllLines(root.resolve("docs/requirements-test-map.tsv")).stream().filter(l->!l.isBlank()&&!l.startsWith("#")).map(l->l.split("\t",-1)).toList();
        StringBuilder out=new StringBuilder("# Truy vết yêu cầu và đề xuất chờ duyệt\n\nNguồn khảo sát: [requirements-sources.md](requirements-sources.md). **8 mã có định nghĩa thật, 48 mã thiếu định nghĩa.** Công cụ không biến đề xuất thành yêu cầu chính thức. Các liên kết TC dưới đây đều chờ chủ đồ án duyệt.\n\n## UC → FR → TC theo định nghĩa tìm được\n\n| UC | Định nghĩa UC | FR / định nghĩa | TC | Cách kiểm | Trạng thái |\n|---|---|---|---|---|---|\n");
        for(int i=1;i<=24;i++){String uc="UC%02d".formatted(i);String[] row=actual.stream().filter(r->r[0].equals(uc)).findFirst().orElseThrow();out.append("| ").append(uc).append(" | ").append(row[1]).append(" | THIẾU ĐỊNH NGHĨA; chưa có cơ sở gán FR | ").append(row[2]).append(" | ").append(row[3]).append(" | ").append(row[4]).append(" |\n");}
        out.append("\nToàn bộ FR-01..24: THIẾU ĐỊNH NGHĨA; không nối với UC bằng suy đoán trong bảng trên. Đối chiếu từng mã ở [ma trận đầy đủ](traceability-matrix.md).\n\n## NFR → TC / bằng chứng\n\n| NFR | Định nghĩa | TC / bằng chứng | Giới hạn | Trạng thái |\n|---|---|---|---|---|\n");
        for(String[] row:actual)if(row[0].startsWith("NFR-"))out.append("| ").append(row[0]).append(" | ").append(row[1]).append(" | ").append(row[2]).append(" | ").append(row[3]).append(" | ").append(row[4]).append(" |\n");
        out.append("\n## ĐỀ XUẤT, CHỜ DUYỆT — tách riêng, không phải định nghĩa gốc\n\nTệp chỉnh sửa: requirements-proposals.tsv. UC10 giữ nghĩa đã tìm được; việc gắn FR-10 và mọi cặp UC/FR khác vẫn chỉ là đề xuất. Những nhóm có test mở rộng nhưng không có mã TC ghi rõ, không tạo mã mới.\n\n| UC dự kiến | FR dự kiến | Phạm vi đề xuất | TC đề xuất / mức tự động | Trạng thái |\n|---|---|---|---|---|\n");
        Set<String> linked=new TreeSet<>();List<String> noTc=new ArrayList<>();
        for(String line:Files.readAllLines(root.resolve("docs/requirements-proposals.tsv"))){if(line.isBlank()||line.startsWith("#"))continue;String[] row=line.split("\t",-1);if(row.length!=4)throw new IllegalStateException("Dòng đề xuất không hợp lệ");String tests="Test mở rộng không mã TC; cần duyệt bổ sung danh mục";
            if(!row[3].isBlank()){List<String> ids=List.of(row[3].split(","));for(String id:ids)if(!catalog.containsKey(id))throw new IllegalStateException("TC đề xuất không hoạt động: "+id);linked.addAll(ids);tests=ids.stream().map(id->id+(executed.contains(id)?" (tự động)":" (chưa có test gắn mã)")).collect(Collectors.joining("; "));}else noTc.add(row[0]+" / "+row[1]);
            out.append("| ").append(row[0]).append(" | ").append(row[1]).append(" | ").append(row[2]).append(" | ").append(tests).append(" | ĐỀ XUẤT CHỜ DUYỆT |\n");}
        out.append("\nNFR-08 **ĐỀ XUẤT CHỜ DUYỆT**: giao diện tiếng Việt, nhập liệu và in hóa đơn dùng được trên trình duyệt mục tiêu. Bằng chứng đề xuất: manual-tests.md; chưa chạy, chưa gán TC mới. Không coi đây là NFR gốc.\n\n## Khoảng trống cuối bảng\n\n- Theo định nghĩa thật: 23 UC ngoài UC10, toàn bộ 24 FR và NFR-08 chưa có ánh xạ chính thức; đây là thiếu định nghĩa/duyệt.\n- NFR-06/07 có bằng chứng kỹ thuật nhưng chưa có mã TC liên kết; trạng thái kiểm chứng xem cột bằng chứng ở bảng NFR.\n- Ngay cả nếu duyệt bản nháp, các nhóm chưa có mã TC: ").append(String.join(", ",noTc)).append("; NFR-08 chưa có TC. Không coi test mở rộng là chưa chạy.\n- Các mã đã ánh xạ nhưng chưa có invocation vẫn nằm trong test-results.md, không tính đạt.\n\n### TC chưa gắn UC/FR nào (kể cả bản đề xuất)\n\n");
        catalog.keySet().stream().filter(id->!linked.contains(id)).forEach(id->out.append("- ").append(id).append(" — ").append(catalog.get(id)).append("\n"));
        out.append("\nDanh sách này sinh bằng phép hiệu tập hợp trên toàn bộ mã đang dùng; không dùng TC-WEB-26. Trước khi duyệt, mọi FR vẫn chưa được liên kết chính thức dù bản đề xuất đã có TC. Các TC còn lại có thể bổ sung vào cùng nhóm sau khi đối chiếu assertion, không nhất thiết tạo UC/FR mới.\n");
        Files.writeString(root.resolve("docs/traceability.md"),out);
    }

}
