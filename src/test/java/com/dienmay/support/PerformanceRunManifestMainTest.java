package com.dienmay.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PerformanceRunManifestMainTest {
    @TempDir Path temp;
    private static final Instant START=Instant.parse("2026-10-27T13:00:00Z");
    private static Clock at(Instant instant){return Clock.fixed(instant,ZoneOffset.UTC);}

    @Test void start_previousPerformanceOutputs_removesThemAndPreservesDefaultEvidence()throws Exception {
        for(String folder:List.of("performance-reports","performance-results","surefire-reports","failsafe-reports")){
            Path file=temp.resolve("target/"+folder+"/old.txt");Files.createDirectories(file.getParent());Files.writeString(file,"keep only defaults");
        }
        PerformanceRunManifestMain.start(temp,at(START));
        assertThat(temp.resolve("target/performance-reports/old.txt")).doesNotExist();
        assertThat(temp.resolve("target/performance-results/old.txt")).doesNotExist();
        assertThat(Files.readString(temp.resolve("target/surefire-reports/old.txt"))).isEqualTo("keep only defaults");
        assertThat(temp.resolve("target/failsafe-reports/old.txt")).exists();
        assertThatThrownBy(()->PerformanceRunManifestMain.finish(temp,at(START.plusSeconds(2)))).hasMessageContaining("Không có artifact");
        assertThat(TestingDocumentation.performanceEvidence(temp,List.of()).verified()).isFalse();
    }

    @Test void finish_freshXmlAndArtifact_createsManifestAcceptedByReportGenerator()throws Exception {
        PerformanceRunManifestMain.start(temp,at(START));freshOutputs(false,START.plusSeconds(1));
        var manifest=PerformanceRunManifestMain.finish(temp,at(START.plusSeconds(2)));
        assertThat(manifest.getProperty("started")).isEqualTo(START.toString());assertThat(manifest.getProperty("finished")).isEqualTo(START.plusSeconds(2).toString());
        assertThat(manifest.getProperty("exitCode")).isEqualTo("0");assertThat(manifest.getProperty("testExecutions")).isEqualTo("1");
        var runs=TestReportMain.readResults(temp);var evidence=TestingDocumentation.performanceEvidence(temp,runs.performanceResults());
        assertThat(evidence.verified()).isTrue();assertThat(evidence.artifact()).contains("HTTP HTML + API + local assets");
        assertThat(evidence.metadata().getProperty("exitCodeScope")).contains("not the entire Maven process");
    }

    @Test void finish_failedAssertion_keepsRealFailureManifestBeforeFailsafeVerify()throws Exception {
        PerformanceRunManifestMain.start(temp,at(START));freshOutputs(true,START.plusSeconds(1));
        var manifest=PerformanceRunManifestMain.finish(temp,at(START.plusSeconds(2)));
        assertThat(manifest.getProperty("exitCode")).isEqualTo("1");assertThat(manifest.getProperty("testFailures")).isEqualTo("1");
        var runs=TestReportMain.readResults(temp);var evidence=TestingDocumentation.performanceEvidence(temp,runs.performanceResults());
        assertThat(evidence.verified()).isTrue();
        String report=TestReportMain.render(Map.of("TC-RPT-22","Hiệu năng"),Map.of("P#page",List.of("TC-RPT-22")),runs.acceptedResults(true));
        assertThat(report).contains("| TC-RPT-22 | Hiệu năng | Đã có test | 1 | Không đạt |");
    }

    @Test void finish_outputCopiedFromEarlierRun_refusesStaleMeasurement()throws Exception {
        PerformanceRunManifestMain.start(temp,at(START));freshOutputs(false,START.minusSeconds(600));
        assertThatThrownBy(()->PerformanceRunManifestMain.finish(temp,at(START.plusSeconds(2)))).hasMessageContaining("cũ");
        var evidence=TestingDocumentation.performanceEvidence(temp,TestReportMain.readResults(temp).performanceResults());
        assertThat(evidence.verified()).isFalse();assertThat(evidence.reason()).contains("ngoài khoảng thời gian");
    }

    @Test void start_targetSymlink_refusesWithoutDeletingExternalData()throws Exception {
        Path outside=Files.createDirectory(temp.resolve("outside"));Files.writeString(outside.resolve("keep.txt"),"untouched");
        Files.createSymbolicLink(temp.resolve("target"),outside);
        assertThatThrownBy(()->PerformanceRunManifestMain.start(temp,at(START))).hasMessageContaining("liên kết tượng trưng");
        assertThat(Files.readString(outside.resolve("keep.txt"))).isEqualTo("untouched");
    }

    private void freshOutputs(boolean failed,Instant time)throws Exception {
        Path xml=temp.resolve("target/performance-reports/TEST-page.xml"),artifact=temp.resolve("target/performance-results/report-pages.md");
        Files.writeString(xml,"<testsuite><testcase classname='P' name='page'>"+(failed?"<failure message='over 2 seconds'/>":"")+"</testcase></testsuite>");
        Files.writeString(artifact,"HTTP HTML + API + local assets; fixture only, not project benchmark.\n");
        for(Path file:List.of(xml,artifact))Files.setLastModifiedTime(file,FileTime.from(time));
    }
}
