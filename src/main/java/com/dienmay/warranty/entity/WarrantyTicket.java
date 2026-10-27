package com.dienmay.warranty.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.context.ActorType;
import com.dienmay.organization.entity.Branch;

@Entity @Table(name="warranty_ticket")
public class WarrantyTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true, length=40)
    private String code;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="warranty_id")
    private Warranty warranty;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="serial_item_id")
    private SerialItem serialItem;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id")
    private Branch branch;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    private WarrantyTicketStatus status;
    @Column(nullable=false,length=1000)
    private String issueDescription;
    @Column(nullable=false)
    private boolean outOfWarranty;
    @Column(length=1000)
    private String overrideReason;
    @Column(length=1000)
    private String diagnosis;
    @Column(length=1000)
    private String resolution;
    @Column(length=1000)
    private String rejectReason;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10)
    private ActorType actorType;
    
    private Long actorId;
    @Column(nullable=false)
    private LocalDateTime receivedAt;
    
    private LocalDateTime completedAt;
    
    private LocalDateTime returnedAt;
    @Version @Column(nullable=false)
    private Long version;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String value) { code=value; }
    public Warranty getWarranty() { return warranty; }
    public void setWarranty(Warranty value) { warranty=value; }
    public SerialItem getSerialItem() { return serialItem; }
    public void setSerialItem(SerialItem value) { serialItem=value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch=value; }
    public WarrantyTicketStatus getStatus() { return status; }
    public void setStatus(WarrantyTicketStatus value) { status=value; }
    public String getIssueDescription() { return issueDescription; }
    public void setIssueDescription(String value) { issueDescription=value; }
    public boolean isOutOfWarranty() { return outOfWarranty; }
    public void setOutOfWarranty(boolean value) { outOfWarranty=value; }
    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String value) { overrideReason=value; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String value) { diagnosis=value; }
    public String getResolution() { return resolution; }
    public void setResolution(String value) { resolution=value; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String value) { rejectReason=value; }
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType value) { actorType=value; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { actorId=value; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { receivedAt=value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt=value; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime value) { returnedAt=value; }
    public Long getVersion() { return version; }
}
