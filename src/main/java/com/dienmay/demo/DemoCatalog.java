package com.dienmay.demo;

import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

@Component @Profile("demo & !prod & !dev & !test & !perf")
public class DemoCatalog {
    public record Item(int ordinal,String sku,String category,String brand,String name,BigDecimal price,int months,boolean serial,String type,String image){}
    private final byte[] source;
    private final List<Item> items;
    public DemoCatalog()throws java.io.IOException{
        try(var stream=new ClassPathResource("demo/products.csv").getInputStream()){source=stream.readAllBytes();}
        var lines=new String(source,StandardCharsets.UTF_8).lines().skip(1).filter(l->!l.isBlank()).toList();var all=new ArrayList<Item>();
        for(String line:lines){String[] c=line.split(";",-1);all.add(new Item(all.size()+1,c[0],c[1],c[2],c[3],new BigDecimal(c[4]),Integer.parseInt(c[5]),Boolean.parseBoolean(c[6]),c[7],c[8]));}
        items=List.copyOf(all);
    }
    public List<Item> items(){return items;}
    public String hash(){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
    public MultipartFile image(Item item){
        try(var stream=new ClassPathResource("demo/images/"+item.image()).getInputStream()){return new Asset(item.image(),stream.readAllBytes());}catch(java.io.IOException error){throw new IllegalStateException("Thiếu ảnh demo "+item.image(),error);}
    }
    private record Asset(String filename,byte[] content) implements MultipartFile {
        public String getName(){return "image";}public String getOriginalFilename(){return filename;}public String getContentType(){return "image/png";}
        public boolean isEmpty(){return content.length==0;}public long getSize(){return content.length;}public byte[] getBytes(){return content.clone();}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(content);}
        public void transferTo(java.io.File target)throws java.io.IOException{java.nio.file.Files.write(target.toPath(),content);}
    }
}
