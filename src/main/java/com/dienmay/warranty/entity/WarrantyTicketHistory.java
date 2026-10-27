package com.dienmay.warranty.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.dienmay.inventory.context.ActorType;
@Entity @Table(name="warranty_ticket_history")
public class WarrantyTicketHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ticket_id") private WarrantyTicket ticket;
    @Enumerated(EnumType.STRING) private WarrantyTicketStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private WarrantyTicketStatus toStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ActorType actorType;
    private Long actorId;
    @Column(length=1000) private String note;
    @Column(nullable=false) private LocalDateTime changedAt;
    protected WarrantyTicketHistory() { }
    public WarrantyTicketHistory(WarrantyTicket ticket, WarrantyTicketStatus from, WarrantyTicketStatus to,
            Long employeeId, String note, LocalDateTime at) {
        this.ticket=ticket; fromStatus=from; toStatus=to; actorType=ActorType.EMPLOYEE;
        actorId=employeeId; this.note=note; changedAt=at;
    }
    public Long getId(){return id;}
    public WarrantyTicketStatus getFromStatus(){return fromStatus;}
    public WarrantyTicketStatus getToStatus(){return toStatus;}
    public ActorType getActorType(){return actorType;}
    public Long getActorId(){return actorId;}
    public String getNote(){return note;}
    public LocalDateTime getChangedAt(){return changedAt;}
}
