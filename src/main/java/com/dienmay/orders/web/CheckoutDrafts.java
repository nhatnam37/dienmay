package com.dienmay.orders.web;
import java.io.Serializable;
import java.util.*;
// Snapshot ID/qty gắn token, giữ tối đa 10 form/session, gồm cả sau thành công để retry POST
// không bị giỏ đã xóa làm thay đổi fingerprint. Giá luôn được lấy lại từ DB trong service.
public class CheckoutDrafts implements Serializable {
    private static final long serialVersionUID=1L;
    public record Draft(Long customerId,Map<Long,Integer> items) implements Serializable { }
    private final LinkedHashMap<String,Draft> drafts=new LinkedHashMap<>();
    public synchronized String open(Long customerId,Map<Long,Integer> items){String token=UUID.randomUUID().toString();drafts.put(token,new Draft(customerId,Map.copyOf(items)));while(drafts.size()>10)drafts.remove(drafts.keySet().iterator().next());return token;}
    public synchronized Draft get(String token,Long customerId){Draft draft=drafts.get(token);return draft!=null&&draft.customerId().equals(customerId)?draft:null;}
}
