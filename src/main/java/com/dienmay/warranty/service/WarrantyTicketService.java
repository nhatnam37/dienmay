package com.dienmay.warranty.service;
import com.dienmay.warranty.entity.*;
import com.dienmay.warranty.dto.*;
import com.dienmay.warranty.mapper.WarrantyTicketMapper;
import com.dienmay.warranty.repository.*;
import com.dienmay.warranty.security.WarrantyAccess;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.organization.entity.Employee;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
public class WarrantyTicketService {
    private final WarrantyRepository warranties;
    private final WarrantyTicketRepository tickets;
    private final WarrantyTicketHistoryRepository histories;
    private final SerialItemRepository serials;
    private final ProductRepository products;
    private final SerialStateService states;
    private final WarrantyAccess access;
    private final SerialNumberRules rules;
    private final WarrantyStatusPolicy policy;
    private final WarrantyTicketMapper mapper;
    private final Clock clock;
    public WarrantyTicketService(WarrantyRepository warranties,WarrantyTicketRepository tickets,
            WarrantyTicketHistoryRepository histories,SerialItemRepository serials,ProductRepository products,
            SerialStateService states,WarrantyAccess access,SerialNumberRules rules,WarrantyStatusPolicy policy,
            WarrantyTicketMapper mapper,Clock clock){
        this.warranties=warranties;this.tickets=tickets;this.histories=histories;this.serials=serials;
        this.products=products;this.states=states;this.access=access;this.rules=rules;this.policy=policy;
        this.mapper=mapper;this.clock=clock;
    }
    public WarrantyTicketView receive(ReceiveRequest request,SalesActor actor){
        Employee employee=access.validate(actor);
        if(request==null) throw new BusinessException("Thiếu yêu cầu tiếp nhận.");
        String issue=required(request.issueDescription(),"Mô tả lỗi");
        Long serialId=request.warrantyId()!=null?warranties.findSerialIdentity(request.warrantyId()).orElseThrow(this::missing):
                serials.findIdentityBySerialNo(rules.normalize(request.serialNo())).orElseThrow(this::missing);
        SerialItem serial=lockSerial(serialId);
        if(serial.getStatus()!=SerialStatus.SOLD) throw new BusinessException("Serial phải ở trạng thái SOLD để tiếp nhận bảo hành.");
        // Chỉ đọc hồ sơ sau khi đã chờ khóa: trả hàng có thể vừa VOIDED hồ sơ trước đó.
        Warranty warranty=request.warrantyId()!=null?warranties.findById(request.warrantyId()).orElseThrow(this::missing):
                warranties.findBySerialItemIdAndStatus(serialId,WarrantyStatus.ACTIVE).orElseThrow(this::missing);
        if(warranty.getStatus()!=WarrantyStatus.ACTIVE || !warranty.getSerialItem().getId().equals(serialId))
            throw new BusinessException("Hồ sơ bảo hành không còn hiệu lực (VOIDED).");
        if(request.serialNo()!=null && !rules.normalize(request.serialNo()).equals(serial.getSerialNo()))
            throw new BusinessException("Serial không khớp hồ sơ bảo hành.");
        if(tickets.existsOpen(serialId)) throw new BusinessException("Serial đã có phiếu bảo hành đang mở.");
        boolean expired=!policy.evaluate(warranty).valid();
        String override=optional(request.overrideReason());
        if(expired){
            if(employee.getRole().getCode().equals("WARRANTY_STAFF")) throw new BusinessException("Bảo hành đã hết hạn; cần quản lý phê duyệt.");
            override=required(override,"Lý do tiếp nhận ngoài bảo hành");
        }
        WarrantyTicket ticket=new WarrantyTicket();
        ticket.setCode("TMP-"+UUID.randomUUID());ticket.setWarranty(warranty);ticket.setSerialItem(serial);
        // ADMIN cũng tiếp nhận tại chi nhánh tài khoản; không bind branch từ request.
        ticket.setBranch(employee.getBranch());ticket.setStatus(WarrantyTicketStatus.RECEIVED);
        ticket.setIssueDescription(issue);ticket.setOutOfWarranty(expired);ticket.setOverrideReason(override);
        ticket.setActorType(ActorType.EMPLOYEE);ticket.setActorId(employee.getId());ticket.setReceivedAt(now());
        tickets.saveAndFlush(ticket);
        ticket.setCode("BH-"+ticket.getReceivedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"))+"-"+String.format(Locale.ROOT,"%05d",ticket.getId()));
        states.transition(serial,SerialStatus.IN_WARRANTY_REPAIR,context(ticket,employee,issue));
        history(ticket,null,employee,issue);tickets.flush();return mapper.view(ticket);
    }
    public WarrantyTicketView startRepair(Long id,Long version,SalesActor actor){return change(id,version,WarrantyTicketStatus.IN_REPAIR,null,null,actor);}
    public WarrantyTicketView complete(Long id,Long version,String diagnosis,String resolution,SalesActor actor){
        return change(id,version,WarrantyTicketStatus.COMPLETED,required(diagnosis,"Chẩn đoán"),required(resolution,"Kết quả sửa chữa"),actor);
    }
    public WarrantyTicketView reject(Long id,Long version,String reason,SalesActor actor){return change(id,version,WarrantyTicketStatus.REJECTED,required(reason,"Lý do từ chối"),null,actor);}
    public WarrantyTicketView returnToCustomer(Long id,Long version,SalesActor actor){return change(id,version,WarrantyTicketStatus.RETURNED,null,null,actor);}
    public WarrantyTicketView cancel(Long id,Long version,String reason,SalesActor actor){return change(id,version,WarrantyTicketStatus.CANCELLED,required(reason,"Lý do hủy"),null,actor);}
    private WarrantyTicketView change(Long id,Long version,WarrantyTicketStatus target,String first,String second,SalesActor actor){
        Employee employee=access.validate(actor);
        Long serialId=tickets.findSerialIdentity(id).orElseThrow(this::missing);
        SerialItem serial=lockSerial(serialId);
        WarrantyTicket ticket=tickets.findById(id).orElseThrow(this::missing);
        access.requireBranch(employee,ticket.getBranch().getId());
        // @Version bảo vệ mọi writer; expectedVersion còn phát hiện form cũ dù lệnh đến sau giao dịch trước.
        // Khóa product -> Serial cùng thứ tự với đổi trả, tránh vòng chờ khóa chéo giữa hai nghiệp vụ.
        if(version==null || !version.equals(ticket.getVersion())) throw new BusinessException("Phiếu đã được cập nhật, tải lại.");
        WarrantyTicketStatus from=ticket.getStatus();
        if(!from.canTransitionTo(target)) throw new BusinessException("Không thể chuyển phiếu từ "+from+" sang "+target+".");
        ticket.setStatus(target);
        if(target==WarrantyTicketStatus.COMPLETED){ticket.setDiagnosis(first);ticket.setResolution(second);ticket.setCompletedAt(now());}
        if(target==WarrantyTicketStatus.REJECTED) ticket.setRejectReason(first);
        if(target==WarrantyTicketStatus.RETURNED || target==WarrantyTicketStatus.CANCELLED){
            states.transition(serial,SerialStatus.SOLD,context(ticket,employee,first));
            if(target==WarrantyTicketStatus.RETURNED) ticket.setReturnedAt(now());
        }
        history(ticket,from,employee,first);tickets.flush();return mapper.view(ticket);
    }
    private SerialItem lockSerial(Long id){
        Long product=serials.findProductIdentity(id).orElseThrow(this::missing);
        products.findForUpdate(product).orElseThrow(this::missing);
        return serials.findByIdForUpdate(id).orElseThrow(this::missing);
    }
    private TransitionContext context(WarrantyTicket ticket,Employee employee,String note){return new TransitionContext("WARRANTY_TICKET",ticket.getId(),ActorType.EMPLOYEE,employee.getId(),note,null);}
    private void history(WarrantyTicket ticket,WarrantyTicketStatus from,Employee employee,String note){histories.save(new WarrantyTicketHistory(ticket,from,ticket.getStatus(),employee.getId(),note,now()));}
    private LocalDateTime now(){return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));}
    private String required(String value,String name){String result=optional(value);if(result==null)throw new BusinessException(name+" không được để trống.");return result;}
    private String optional(String value){if(value==null || value.isBlank())return null;String result=value.strip();if(result.length()>1000)throw new BusinessException("Nội dung không được vượt quá 1000 ký tự.");return result;}
    private ResourceNotFoundException missing(){return new ResourceNotFoundException("Không tìm thấy hồ sơ bảo hành.");}
}
