package com.dienmay.demo;

import jakarta.persistence.*;

@Entity @Table(name="demo_seed_step")
public class DemoSeedStep {
    @Id @Column(length=160) private String id;
    @Column(name="result_id",nullable=false) private long result;
    public DemoSeedStep(){}
    public DemoSeedStep(String id,long result){this.id=id;this.result=result;}
    public long getResult(){return result;}
}
