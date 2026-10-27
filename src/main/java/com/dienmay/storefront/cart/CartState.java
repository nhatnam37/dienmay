package com.dienmay.storefront.cart;
import java.io.Serializable;
import java.util.*;
public class CartState implements Serializable {
    private static final long serialVersionUID=1L;
    // Session chỉ giữ ID/số lượng, không giữ entity, giá, Serial hoặc tồn kho snapshot.
    private final Map<Long,Integer> items=new LinkedHashMap<>();
    public synchronized Map<Long,Integer> snapshot(){return Collections.unmodifiableMap(new LinkedHashMap<>(items));}
    synchronized void put(Long id,int quantity){items.put(id,quantity);}
    synchronized void remove(Long id){items.remove(id);}
    synchronized void clear(){items.clear();}
}
