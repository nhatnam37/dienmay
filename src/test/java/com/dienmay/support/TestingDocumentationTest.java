package com.dienmay.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TestingDocumentationTest {
    @TempDir Path temp;
    @Test void generateTraceability_unknownOrRetiredCase_rejectsMapping()throws Exception{
        Files.createDirectories(temp.resolve("docs"));Files.writeString(temp.resolve("docs/requirements-test-map.tsv"),"UC10\tTra Serial\tTC-WEB-26\tTự động\tCHỜ DUYỆT\n");
        assertThatThrownBy(()->TestingDocumentation.generateTraceability(temp,Map.of(),Map.of(),Set.of())).hasMessageContaining("TC không hoạt động");
    }
    @Test void generateTraceability_missingRequirements_rejectsIncompleteMatrix()throws Exception{
        Files.createDirectories(temp.resolve("docs"));Files.writeString(temp.resolve("docs/requirements-test-map.tsv"),"UC10\tTra Serial\tTC-INV-17\tTự động\tCHỜ DUYỆT\n");
        assertThatThrownBy(()->TestingDocumentation.generateTraceability(temp,Map.of("TC-INV-17","Tra Serial"),Map.of(),Set.of())).hasMessageContaining("Ma trận phải có đúng");
    }
    @Test void generateTraceability_pendingDefinitions_preservesGapsAndMissingTests()throws Exception{
        Files.createDirectories(temp.resolve("docs"));Files.copy(Path.of("docs/requirements-test-map.tsv"),temp.resolve("docs/requirements-test-map.tsv"));
        Files.copy(Path.of("docs/requirements-proposals.tsv"),temp.resolve("docs/requirements-proposals.tsv"));
        TestingDocumentation.generateTraceability(temp,TestReportMain.catalog(Path.of("docs/test-cases.md")),Map.of(),Set.of("TC-INV-17"));
        var run=new Properties();run.setProperty("evidence","docs/evidence/run");
        for(String folder:List.of("surefire-reports","failsafe-reports")){
            Files.createDirectories(temp.resolve("target/"+folder));Files.createDirectories(temp.resolve("docs/evidence/run/"+folder));
            Files.writeString(temp.resolve("target/"+folder+"/TEST-demo.xml"),"same");Files.writeString(temp.resolve("docs/evidence/run/"+folder+"/TEST-demo.xml"),"same");
        }
        Files.createDirectories(temp.resolve("target/site/jacoco"));Files.writeString(temp.resolve("target/site/jacoco/jacoco.xml"),"coverage");Files.writeString(temp.resolve("docs/evidence/run/jacoco.xml"),"coverage");
        assertThat(TestingDocumentation.matchesEvidence(temp,run)).isTrue();
        Files.writeString(temp.resolve("target/surefire-reports/TEST-demo.xml"),"new run");
        assertThat(TestingDocumentation.matchesEvidence(temp,run)).isFalse();
        assertThat(Files.readString(temp.resolve("docs/traceability-matrix.md"))).contains("48 mã chưa thể đối chiếu","TC-INV-17: có test tự động","TC-RPT-22: chưa có test tự động","DỰ THẢO CHỜ DUYỆT");
    }
    @Test void matchesEvidence_hashManifest_checksExactXmlSetAndCoverageWithoutRawSnapshot()throws Exception {
        Properties run=new Properties();run.setProperty("format","hashes-v1");run.setProperty("evidence","docs/evidence/backend-v2");
        for(String folder:List.of("surefire-reports","failsafe-reports")){
            Path file=temp.resolve("target/"+folder+"/TEST-demo.xml");Files.createDirectories(file.getParent());Files.writeString(file,"<testsuite/>");
            run.setProperty("sha256."+folder+"/TEST-demo.xml",TestingDocumentation.sha256(file));
        }
        Path coverage=temp.resolve("target/site/jacoco/jacoco.xml");Files.createDirectories(coverage.getParent());Files.writeString(coverage,"<report/>");
        run.setProperty("sha256.jacoco.xml",TestingDocumentation.sha256(coverage));
        assertThat(TestingDocumentation.matchesEvidence(temp,run)).isTrue();
        Files.writeString(coverage,"<report name='new run'/>");assertThat(TestingDocumentation.matchesEvidence(temp,run)).isFalse();
        run.setProperty("sha256.jacoco.xml",TestingDocumentation.sha256(coverage));
        Files.writeString(temp.resolve("target/surefire-reports/TEST-extra.xml"),"<testsuite/>");
        assertThat(TestingDocumentation.matchesEvidence(temp,run)).isFalse();
        Files.delete(temp.resolve("target/surefire-reports/TEST-extra.xml"));
        run.setProperty("sha256.failsafe-reports/TEST-stale.xml","old hash");
        assertThat(TestingDocumentation.matchesEvidence(temp,run)).isFalse();
        run.remove("sha256.failsafe-reports/TEST-stale.xml");run.setProperty("evidence","../outside");
        assertThat(TestingDocumentation.matchesEvidence(temp,run)).isFalse();
    }
    @Test void performanceEvidence_missingArtifact_orMissingXml_isNotAccepted()throws Exception {
        var row=new TestReportMain.Result("P","page",0.4,"2026-10-27",false,false,"");
        var missing=TestingDocumentation.performanceEvidence(temp,List.of(row));
        assertThat(missing.verified()).isFalse();assertThat(missing.artifact()).isEmpty();assertThat(missing.reason()).contains("Thiếu artifact");
        assertThat(TestingDocumentation.performanceEvidence(temp,List.of()).reason()).contains("Chưa thực hiện");
    }
    @Test void performanceEvidence_matchedManifest_preservesSeparateMetadataAndMeasuredArtifact()throws Exception {
        var run=performanceFixture();
        var rows=List.of(new TestReportMain.Result("P","page[1]",0.4,"2026-10-27T20:00:01+07:00",false,false,""));
        var evidence=TestingDocumentation.performanceEvidence(temp,rows);assertThat(evidence.verified()).isTrue();
        var defaultRow=new TestReportMain.Result("C","unit",0.1,"2026-10-27T21:00:00+07:00",false,false,"");
        var scopes=TestingDocumentation.renderRunScopes(new TestReportMain.RunResults(List.of(defaultRow),rows),Map.of("P#page",List.of("TC-RPT-22")),evidence);
        assertThat(scopes).contains("| Mặc định: Surefire + Failsafe | 1 | 0 | 1 | 0 | 0 | Có |","| Performance: profile perf | 1 | 1 | 0 | 0 | 0 | Có |","command: ./mvnw -Pperf verify","commit: perf-commit","Trang dashboard: 0,4 giây","không phải một lượt verify mặc định");
        assertThat(evidence.metadata().getProperty("started")).isEqualTo(run.getProperty("started"));
    }
    @Test void performanceEvidence_staleXmlOrArtifact_isRejectedEvenWhenOneHashStillMatches()throws Exception {
        performanceFixture();var rows=List.of(new TestReportMain.Result("P","page",0.4,"2026-10-27",false,false,""));
        Path artifact=temp.resolve("target/performance-results/report-pages.md");
        Files.writeString(artifact,"Số đo cũ bị thay");assertThat(TestingDocumentation.performanceEvidence(temp,rows).reason()).contains("Hash artifact không khớp");
        performanceFixture();
        Files.writeString(temp.resolve("target/performance-reports/TEST-pages.xml"),"<testsuite><testcase classname='P' name='new'/></testsuite>");
        assertThat(TestingDocumentation.performanceEvidence(temp,rows).reason()).contains("Hash XML performance không khớp");
        performanceFixture();
        Files.setLastModifiedTime(artifact,FileTime.from(Instant.parse("2026-10-26T13:00:01Z")));
        var stale=TestingDocumentation.performanceEvidence(temp,rows);
        assertThat(stale.verified()).isFalse();assertThat(stale.artifact()).isEmpty();assertThat(stale.reason()).contains("ngoài khoảng thời gian");
    }
    @Test void enrich_defaultAndPerformanceRuns_keepsMetadataIndependentAndFullCatalog()throws Exception {
        performanceFixture();Properties run=new Properties();run.setProperty("format","hashes-v1");run.setProperty("evidence","docs/evidence/backend-v2");run.setProperty("command","./mvnw verify");run.setProperty("commit","default-commit");
        for(String folder:List.of("surefire-reports","failsafe-reports")){
            Path file=temp.resolve("target/"+folder+"/TEST-default.xml");Files.createDirectories(file.getParent());Files.writeString(file,"<testsuite><testcase classname='C' name='extra'/></testsuite>");
            run.setProperty("sha256."+folder+"/TEST-default.xml",TestingDocumentation.sha256(file));
        }
        Path coverage=temp.resolve("target/site/jacoco/jacoco.xml");Files.createDirectories(coverage.getParent());Files.writeString(coverage,"<report/>");run.setProperty("sha256.jacoco.xml",TestingDocumentation.sha256(coverage));
        Path latest=temp.resolve("docs/evidence/automated/latest-run.properties");Files.createDirectories(latest.getParent());try(var out=Files.newOutputStream(latest)){run.store(out,"Default fixture");}
        for(String file:List.of("requirements-test-map.tsv","requirements-proposals.tsv"))Files.copy(Path.of("docs/"+file),temp.resolve("docs/"+file));
        var catalog=TestReportMain.catalog(Path.of("docs/test-cases.md"));var mapping=Map.of("P#page",List.of("TC-RPT-22"));
        var runs=TestReportMain.readResults(temp);var performance=TestingDocumentation.performanceEvidence(temp,runs.performanceResults());var rows=runs.acceptedResults(performance.verified());
        var report=TestingDocumentation.enrich(temp,TestReportMain.render(catalog,mapping,rows),catalog,mapping,rows,runs,performance);
        String defaultHeader=report.substring(report.indexOf("## Thông tin lần chạy verify mặc định"),report.indexOf("## Phạm vi từng bộ test"));
        assertThat(defaultHeader).contains("command: ./mvnw verify","commit: default-commit","manifest SHA-256").doesNotContain("perf-commit");
        String performanceHeader=report.substring(report.indexOf("## Performance — lần chạy riêng"),report.indexOf("## Tổng hợp\n"));
        assertThat(performanceHeader).contains("command: ./mvnw -Pperf verify","commit: perf-commit").doesNotContain("default-commit");
        assertThat(report).contains("Tổng số mã đang dùng: 162","Tổng số test (lượt thực thi, gồm tham số hóa): 3","| TC-RPT-22 | Hiệu năng (NFR-04) | Đã có test | 1 | Đạt |");
    }
    @Test void coverageSummary_verifiedArtifact_readsOnlyRootCountersAndDoesNotLoadExternalDtd()throws Exception {
        Path file=temp.resolve("target/site/jacoco/jacoco.xml");Files.createDirectories(file.getParent());
        Files.writeString(file,"<!DOCTYPE report SYSTEM 'file:///not-a-real-report.dtd'><report><package name='nested'><counter type='LINE' covered='999' missed='0'/></package><counter type='LINE' covered='82' missed='18'/><counter type='BRANCH' covered='69' missed='31'/></report>");
        assertThat(TestingDocumentation.coverageSummary(temp,true)).contains("| LINE | 82 | 100 | 82.00% |","| BRANCH | 69 | 100 | 69.00% |").doesNotContain("999");
        assertThat(TestingDocumentation.coverageSummary(temp,false)).contains("Chưa xác minh").doesNotContain("82.00%");
    }
    private Properties performanceFixture()throws Exception {
        Path artifact=temp.resolve("target/performance-results/report-pages.md"),xml=temp.resolve("target/performance-reports/TEST-pages.xml");
        Files.createDirectories(artifact.getParent());Files.createDirectories(xml.getParent());
        Files.writeString(artifact,"### Máy đo\n\nJDK 21, MySQL 8\n\nTrang dashboard: 0,4 giây\n");
        Files.writeString(xml,"<testsuite><testcase classname='P' name='page[1]'/></testsuite>");
        for(Path file:List.of(artifact,xml))Files.setLastModifiedTime(file,FileTime.from(Instant.parse("2026-10-27T13:00:01Z")));
        Properties run=new Properties();run.setProperty("started","2026-10-27T13:00:00Z");run.setProperty("finished","2026-10-27T13:00:02Z");
        run.setProperty("command","./mvnw -Pperf verify");run.setProperty("exitCode","0");run.setProperty("commit","perf-commit");
        run.setProperty("artifactSha256",TestingDocumentation.sha256(artifact));run.setProperty("xml.TEST-pages.xml.sha256",TestingDocumentation.sha256(xml));
        try(var out=Files.newOutputStream(artifact.resolveSibling("run.properties"))){run.store(out,"Fixture only: no measured project result.");}
        return run;
    }
}
