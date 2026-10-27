package com.dienmay.support;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;

/** Hook của profile perf: chỉ quản lý output sinh ra, không chạy test hoặc gọi Git. */
public final class PerformanceRunManifestMain {
    private PerformanceRunManifestMain() {}

    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Cần start|finish và đường dẫn project.");
        Path root=Path.of(args[1]).toAbsolutePath().normalize();
        if(args[0].equals("start"))start(root,Clock.systemUTC());
        else if(args[0].equals("finish"))finish(root,Clock.systemUTC());
        else throw new IllegalArgumentException("Thao tác không hợp lệ: "+args[0]);
    }

    static void start(Path root,Clock clock)throws IOException {
        guardTarget(root);
        // Không clean target: XML/JaCoCo của verify mặc định phải được giữ để đối chiếu riêng.
        for(String folder:List.of("performance-reports","performance-results")){
            Path dir=root.resolve("target/"+folder);deleteGeneratedTree(dir);Files.createDirectories(dir);
        }
        Properties state=new Properties();state.setProperty("started",clock.instant().toString());
        state.setProperty("command","./mvnw -Pperf verify");
        state.setProperty("commandKind","canonical-profile-command; additional Maven CLI flags are not recorded");
        state.setProperty("runId",UUID.randomUUID().toString());
        store(root.resolve("target/performance-results/.started.properties"),state);
        System.out.println("Performance: đã xóa riêng output perf cũ và ghi thời điểm bắt đầu.");
    }

    static Properties finish(Path root,Clock clock)throws Exception {
        guardTarget(root);
        Path dir=root.resolve("target/performance-results"),stateFile=dir.resolve(".started.properties");
        if(!Files.isRegularFile(stateFile))throw new IllegalStateException("Thiếu mốc bắt đầu performance; không nhận output cũ.");
        Properties run=new Properties();try(var in=Files.newInputStream(stateFile)){run.load(in);}
        Instant start=Instant.parse(run.getProperty("started")),finish=clock.instant();
        if(finish.isBefore(start))throw new IllegalStateException("Đồng hồ kết thúc performance nằm trước thời điểm bắt đầu.");
        run.setProperty("finished",finish.toString());run.setProperty("elapsedSeconds",Long.toString(Duration.between(start,finish).toSeconds()));
        run.setProperty("format","performance-hashes-v1");
        run.setProperty("exitCodeScope","performance test XML and required artifact; not the entire Maven process");
        run.setProperty("metadataOrigin","Maven post-integration-test hook, before failsafe:verify");
        List<Path> xml=new ArrayList<>();Path reports=root.resolve("target/performance-reports");
        if(Files.isDirectory(reports))try(var files=Files.list(reports)){xml.addAll(files.filter(p->p.getFileName().toString().startsWith("TEST-")&&p.toString().endsWith(".xml")).sorted().toList());}
        List<TestReportMain.Result> rows=new ArrayList<>();
        String incomplete=xml.isEmpty()?"Không có XML performance của lần chạy hiện tại.":"";
        for(Path file:xml){
            if(!withinRun(file,start,finish))incomplete="XML performance nằm ngoài khoảng thời gian của lần chạy hiện tại.";
            rows.addAll(TestReportMain.parse(file));run.setProperty("xml."+file.getFileName()+".sha256",TestingDocumentation.sha256(file));
        }
        Path artifact=dir.resolve("report-pages.md");
        if(!Files.isRegularFile(artifact))incomplete="Không có artifact report-pages.md của lần chạy hiện tại.";
        else {
            if(!withinRun(artifact,start,finish))incomplete="Artifact performance có thời điểm cũ; không nhập số đo stale.";
            run.setProperty("artifactSha256",TestingDocumentation.sha256(artifact));
        }
        long failures=rows.stream().filter(TestReportMain.Result::failed).count(),skipped=rows.stream().filter(TestReportMain.Result::skipped).count();
        run.setProperty("testExecutions",Integer.toString(rows.size()));run.setProperty("testFailures",Long.toString(failures));run.setProperty("testSkipped",Long.toString(skipped));
        run.setProperty("exitCode",failures==0&&skipped==0&&incomplete.isEmpty()?"0":"1");
        run.setProperty("artifactStatus",incomplete.isEmpty()?"complete":incomplete);
        store(dir.resolve("run.properties"),run);
        // Các assertion thất bại vẫn có manifest; failsafe:verify giữ nguyên trách nhiệm báo lỗi test.
        if(!incomplete.isEmpty())throw new IllegalStateException(incomplete);
        System.out.println("Performance manifest: "+rows.size()+" lượt, "+failures+" lỗi, "+skipped+" bỏ qua; đã hash XML và artifact riêng.");
        return run;
    }

    private static boolean withinRun(Path file,Instant start,Instant finish)throws IOException {
        Instant time=Files.getLastModifiedTime(file).toInstant();return !time.isBefore(start.minusSeconds(1))&&!time.isAfter(finish.plusSeconds(1));
    }
    private static void guardTarget(Path root)throws IOException {
        if(Files.isSymbolicLink(root.resolve("target")))throw new IOException("Không quản lý output khi target là liên kết tượng trưng.");
    }
    private static void deleteGeneratedTree(Path dir)throws IOException {
        if(!Files.exists(dir,LinkOption.NOFOLLOW_LINKS))return;
        Files.walkFileTree(dir,new SimpleFileVisitor<>(){
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException{Files.delete(file);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path folder,IOException error)throws IOException{if(error!=null)throw error;Files.delete(folder);return FileVisitResult.CONTINUE;}
        });
    }
    private static void store(Path file,Properties properties)throws IOException {
        Path pending=file.resolveSibling(file.getFileName()+".tmp");
        try(var out=Files.newOutputStream(pending)){properties.store(out,"Performance evidence only; no credentials or Git commands.");}
        Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING);
    }
}
