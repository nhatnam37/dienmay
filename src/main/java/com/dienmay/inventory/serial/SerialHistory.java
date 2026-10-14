package com.dienmay.inventory.serial;

import jakarta.persistence.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.inventory.context.ActorType;
import java.time.LocalDateTime;

@Entity
@Table(name = "serial_history")
public class SerialHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serial_item_id", nullable = false)
    private SerialItem serialItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private SerialStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SerialStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "from_branch_id", nullable = true)
    private Branch fromBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "to_branch_id", nullable = true)
    private Branch toBranch;

    @Column(name = "ref_type", nullable = false, length = 30)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    void onCreate() { changedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public SerialItem getSerialItem() { return serialItem; }
    public void setSerialItem(SerialItem serialItem) { this.serialItem = serialItem; }
    public SerialStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(SerialStatus fromStatus) { this.fromStatus = fromStatus; }
    public SerialStatus getToStatus() { return toStatus; }
    public void setToStatus(SerialStatus toStatus) { this.toStatus = toStatus; }
    public Branch getFromBranch() { return fromBranch; }
    public void setFromBranch(Branch fromBranch) { this.fromBranch = fromBranch; }
    public Branch getToBranch() { return toBranch; }
    public void setToBranch(Branch toBranch) { this.toBranch = toBranch; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
