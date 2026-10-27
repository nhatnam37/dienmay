package com.dienmay.demo;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
@Profile("demo & !prod & !dev & !test & !perf")
public interface DemoStepRepository extends JpaRepository<DemoSeedStep,String>{}
