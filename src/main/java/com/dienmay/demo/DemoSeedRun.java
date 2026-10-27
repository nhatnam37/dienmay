package com.dienmay.demo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="demo_seed_run")
public class DemoSeedRun {
    @Id @Column(length=80) private String id;
    @Column(name="seed_value",nullable=false) private long seed;
    @Column(name="anchor_at",nullable=false) private LocalDateTime anchor;
    @Column(name="catalog_hash",nullable=false,length=64) private String catalogHash;
    @Column(nullable=false) private boolean completed;
    public String getId(){return id;}public void setId(String value){id=value;}
    public long getSeed(){return seed;}public void setSeed(long value){seed=value;}
    public LocalDateTime getAnchor(){return anchor;}public void setAnchor(LocalDateTime value){anchor=value;}
    public String getCatalogHash(){return catalogHash;}public void setCatalogHash(String value){catalogHash=value;}
    public boolean isCompleted(){return completed;}public void setCompleted(boolean value){completed=value;}
}
