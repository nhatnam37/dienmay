package com.dienmay.reports.perf;

import com.dienmay.reports.ReportJdbc;
import com.dienmay.support.TestCase;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.containers.MySQLContainer;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** HTTP thật + MySQL thật, chỉ -Pperf; seed SQL tổng hợp riêng, tuyệt đối không trỏ DB dev/demo. */
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=perf", "app.report.perf.seed=true", "app.report.perf.benchmark=false",
    "app.order.expiry-enabled=false", "server.servlet.session.cookie.secure=false",
    "logging.level.org.springframework.jdbc=warn", "logging.level.org.hibernate.SQL=warn"
})
@Import(ReportPagePerformanceIT.Instrumentation.class)
class ReportPagePerformanceIT {
    private static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("dienmay_report_perf").withUsername("perf").withPassword("perf-test-only")
            .withUrlParam("rewriteBatchedStatements","true")
            .withCommand("--log-bin-trust-function-creators=1");
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.flyway.user",()->"root");r.add("spring.flyway.password",MYSQL::getPassword);
    }
    static final Path OUTPUT=Path.of("target/performance-results/report-pages.md");
    static final String PASSWORD="PerfTest@123456789";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired TimedQueries timings;
    HttpClient client;
    final Map<String,PageResult> results=new LinkedHashMap<>();
    final Map<String,String> environment=new LinkedHashMap<>();
    String base;
    Instant started;
    record Query(String path,String sql,double milliseconds){}
    record Sample(double milliseconds,double htmlMilliseconds,int bytes,List<Query> queries){}
    record PageResult(String path,List<Sample> samples){double max(){return samples.stream().mapToDouble(Sample::milliseconds).max().orElseThrow();}}
    @BeforeAll void prepare()throws Exception{
        started=Instant.now();Files.createDirectories(OUTPUT.getParent());Files.deleteIfExists(OUTPUT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM serial_item",Long.class)).isEqualTo(100000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice",Long.class)).isEqualTo(20000);
        // Fixture benchmark ban đầu không đăng nhập được; chỉ cấp password cho ADMIN của DB test riêng.
        jdbc.update("UPDATE employee SET password_hash=? WHERE username='PERF-1'",passwords.encode(PASSWORD));
        base="http://localhost:"+port;
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        String login=get("/login","text/html").body();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login);assertThat(token.find()).isTrue();
        String form="username=PERF-1&password="+encode(PASSWORD)+"&_csrf="+encode(token.group(1));
        var authenticated=client.send(HttpRequest.newBuilder(URI.create(base+"/login")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(authenticated.statusCode()).isEqualTo(302);assertThat(get("/staff","text/html").body()).contains("ROLE_ADMIN");
        environment.put("OS",System.getProperty("os.name")+" "+System.getProperty("os.version")+" / "+System.getProperty("os.arch"));
        environment.put("Java",System.getProperty("java.runtime.version")+" / "+System.getProperty("java.vendor"));
        environment.put("CPU model",command("sysctl","-n","machdep.cpu.brand_string"));
        environment.put("CPU logical",Integer.toString(Runtime.getRuntime().availableProcessors()));
        environment.put("RAM host bytes",command("sysctl","-n","hw.memsize"));
        environment.put("JVM max heap bytes",Long.toString(Runtime.getRuntime().maxMemory()));
        environment.put("Docker CPU/RAM/version",command("docker","info","--format","{{.NCPU}} CPUs / {{.MemTotal}} bytes / {{.ServerVersion}}"));
        environment.put("MySQL",jdbc.queryForObject("SELECT VERSION()",String.class));
        environment.put("MySQL buffer pool bytes",jdbc.queryForObject("SELECT @@innodb_buffer_pool_size",String.class));
        environment.put("JVM input args",ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(a->a.startsWith("-Xmx")||a.startsWith("-XX:MaxRAM")).toList().toString());
    }
    @ParameterizedTest(name="{0}")
    @ValueSource(strings={"dashboard","revenue","products","inventory","low-stock"})
    @TestCase("TC-RPT-22")
    void page_largeDataset_finishesWithinTwoSeconds(String page)throws Exception{
        List<Sample> samples=new ArrayList<>();String path="/reports/"+page;
        try{
            for(int i=0;i<3;i++)samples.add(measure(path));
        }finally{results.put(path,new PageResult(path,List.copyOf(samples)));writeReport();}
        // Bao gồm lần tải report đầu tiên, không bỏ cold render để làm đẹp số liệu.
        assertThat(samples).hasSize(3);
        assertThat(results.get(path).max()).as("%s max ms; SQL và HTML breakdown ở %s (không tự sửa nếu vượt)",path,OUTPUT).isLessThan(2000);
    }
    private Sample measure(String path)throws Exception{
        timings.clear();long start=System.nanoTime();var response=get(path,"text/html");long htmlDone=System.nanoTime();
        String html=response.body();assertThat(html).contains("report-shell");
        Map<String,String> filters=new LinkedHashMap<>();
        for(String attr:List.of("from","to","branch","granularity","order")){
            var match=Pattern.compile("data-"+attr+"=\"([^\"]*)\"").matcher(html);
            if(match.find()&&!match.group(1).isBlank())filters.put(attr.equals("branch")?"branchId":attr.equals("order")?"orderBy":attr,match.group(1));
        }
        String query=filters.entrySet().stream().map(e->e.getKey()+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
        var charts=Pattern.compile("data-report-chart=\"([a-z-]+)\"").matcher(html);List<String> requests=new ArrayList<>();
        while(charts.find())requests.add("/reports/api/"+charts.group(1)+"?"+query);
        int expectedCharts=path.endsWith("/dashboard")?4:path.endsWith("/revenue")?1:0;
        assertThat(requests).as("Không âm thầm bỏ API biểu đồ của %s",path).hasSize(expectedCharts);
        requests.add("/css/app.css");requests.add("/js/reports.js");
        // Mô phỏng các fetch cùng origin của reports.js; CDN và vẽ canvas không nằm trong phép đo HTTP backend.
        List<CompletableFuture<HttpResponse<String>>> pending=requests.stream().map(uri->client.sendAsync(HttpRequest.newBuilder(URI.create(base+uri)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString())).toList();
        int bytes=html.getBytes(StandardCharsets.UTF_8).length;
        for(int i=0;i<pending.size();i++){var extra=pending.get(i).get(35,TimeUnit.SECONDS);assertThat(extra.statusCode()).as(requests.get(i)).isEqualTo(200);bytes+=extra.body().getBytes(StandardCharsets.UTF_8).length;if(requests.get(i).startsWith("/reports/api/"))assertThat(extra.headers().firstValue("Content-Type").orElse("")).contains("application/json");}
        double elapsed=(System.nanoTime()-start)/1_000_000.0;
        var queries=timings.snapshot();assertThat(queries).as("Instrumentation phải bắt được SELECT report của %s",path).isNotEmpty();
        return new Sample(elapsed,(htmlDone-start)/1_000_000.0,bytes,queries);
    }
    private HttpResponse<String> get(String path,String accept)throws Exception{
        var response=client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Accept",accept).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("HTTP %s, không chấp nhận redirect/login thay cho report",path).isEqualTo(200);return response;
    }
    private static String encode(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static String command(String...args){
        try{Process p=new ProcessBuilder(args).redirectErrorStream(true).start();if(!p.waitFor(5,TimeUnit.SECONDS)){p.destroyForcibly();return "Chưa đọc được (timeout)";}return p.exitValue()==0?new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).strip():"Chưa đọc được trên OS này";}
        catch(InterruptedException e){Thread.currentThread().interrupt();return "Chưa đọc được (interrupted)";}catch(IOException e){return "Chưa đọc được trên OS này";}
    }
    private void writeReport()throws IOException{
        StringBuilder text=new StringBuilder("## TC-RPT-22 — đo HTTP trang báo cáo riêng bằng -Pperf\n\n");
        text.append("- Bắt đầu UTC: ").append(started).append("\n- Ghi kết quả UTC: ").append(Instant.now()).append("\n- Dataset: 100.000 Serial, 20.000 hóa đơn, 40.000 dòng hóa đơn, 10.000 sản phẩm, 100.000 serial_history; MySQL Testcontainers riêng.\n- Ba lượt mỗi trang, gồm lần render report đầu tiên sau seed/ANALYZE và đăng nhập; không cưỡng chế xóa cache DB. Ngưỡng: max < 2.000 ms. Chạy tuần tự từng trang; API biểu đồ cùng trang tải song song như reports.js.\n- Phạm vi: HTTP loopback HTML + API biểu đồ + app.css/reports.js, có login/authorization thật; không gồm tải CDN, browser layout/paint/canvas, TLS, Internet, tải nhiều người dùng hay p95. Login/seed/startup ngoài thời gian đo. Chỉ bộ lọc mặc định 30 ngày và trang 0; không suy ra mọi bộ lọc/trang. Không dùng số service để thay số trang.\n- Mật khẩu fixture và nội dung cá nhân không ghi trong report; SQL chỉ có placeholders. Dataset perf tổng hợp không phải dữ liệu demo hoặc bằng chứng bất biến nghiệp vụ.\n\n| Cấu hình | Giá trị |\n|---|---|\n");
        environment.forEach((k,v)->text.append("| ").append(k).append(" | ").append(v.replace("|","/")).append(" |\n"));
        text.append("\n| Trang | Lượt 1 ms | Lượt 2 ms | Lượt 3 ms | Max ms | Kết quả |\n|---|---:|---:|---:|---:|---|\n");
        for(var row:results.values()){
            text.append("| ").append(row.path()).append(" | ");for(int i=0;i<3;i++)text.append(i<row.samples().size()?String.format(Locale.ROOT,"%.3f",row.samples().get(i).milliseconds()):"chưa thực hiện").append(" | ");
            text.append(String.format(Locale.ROOT,"%.3f",row.samples().isEmpty()?0:row.max())).append(" | ").append(row.samples().size()!=3?"Chưa đủ dữ liệu":row.max()<2000?"Đạt":"VƯỢT 2 GIÂY — chưa sửa").append(" |\n");
        }
        for(var row:results.values())if(!row.samples().isEmpty()){
            var worst=row.samples().stream().max(Comparator.comparingDouble(Sample::milliseconds)).orElseThrow();
            text.append("\n### ").append(row.path()).append(" — lượt chậm nhất\n\nHTML: ").append(String.format(Locale.ROOT,"%.3f",worst.htmlMilliseconds())).append(" ms; HTML + tài nguyên/API: ").append(String.format(Locale.ROOT,"%.3f",worst.milliseconds())).append(" ms; tổng response bytes: ").append(worst.bytes()).append(".\n\nBa SELECT qua ReportJdbc chậm nhất trong lượt đó (không gồm SELECT Hibernate cho nav/meta/auth; wall-clock vẫn gồm tất cả; không kết luận SQL là nguyên nhân nếu HTML/render mới chiếm thời gian):\n");
            for(var q:worst.queries().stream().sorted(Comparator.comparingDouble(Query::milliseconds).reversed()).limit(3).toList())text.append("\n- ").append(q.path()).append(": ").append(String.format(Locale.ROOT,"%.3f",q.milliseconds())).append(" ms\n\n```sql\n").append(q.sql()).append("\n```\n");
        }
        Files.writeString(OUTPUT,text);
    }
    /** Instrumentation chỉ ở test: không chỉnh query, dữ liệu trả về hoặc nghiệp vụ production. */
    @TestConfiguration(proxyBeanMethods=false) static class Instrumentation {
        @Bean @Primary java.time.Clock fixedPerfClock(){return java.time.Clock.fixed(Instant.parse("2026-10-27T02:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));}
        @Bean @Primary TimedQueries timedQueries(NamedParameterJdbcTemplate jdbc){return new TimedQueries(jdbc);}
        @Bean FilterRegistrationBean<OncePerRequestFilter> queryRequestScope(TimedQueries q){
            // ReportJdbc có proxy @Repository: gọi method để chạm target, không đọc field của proxy.
            var filter=new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{q.beginRequest(request.getRequestURI());try{chain.doFilter(request,response);}finally{q.endRequest();}}};
            return new FilterRegistrationBean<>(filter);
        }
    }
    static class TimedQueries extends ReportJdbc {
        final ThreadLocal<String> path=new ThreadLocal<>();final Queue<Query> queries=new ConcurrentLinkedQueue<>();
        TimedQueries(NamedParameterJdbcTemplate jdbc){super(jdbc);}
        @Override public <T> List<T> query(String sql,Map<String,?> p,RowMapper<T> mapper){long t=System.nanoTime();try{return super.query(sql,p,mapper);}finally{if(path.get()!=null)queries.add(new Query(path.get(),sql,(System.nanoTime()-t)/1_000_000.0));}}
        public void beginRequest(String uri){path.set(uri);}public void endRequest(){path.remove();}
        public void clear(){queries.clear();}public List<Query> snapshot(){return List.copyOf(queries);}
    }
}
