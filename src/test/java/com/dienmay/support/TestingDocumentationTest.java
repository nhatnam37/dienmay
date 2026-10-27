package com.dienmay.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
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
}
