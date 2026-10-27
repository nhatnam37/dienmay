package com.dienmay.customer.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@jakarta.persistence.EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Entity @Table(name="customer")
public class Customer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=254) private String email;
    @Column(nullable=false,unique=true,length=10) private String phone;
    @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
    @Column(name="full_name",nullable=false,length=100) private String fullName;
    @Column(length=255) private String address;
    @Column(nullable=false) private boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) @org.springframework.data.annotation.CreatedDate
    private LocalDateTime createdAt;
    public Long getId(){return id;}
    public String getEmail(){return email;} public void setEmail(String value){email=value;}
    public String getPhone(){return phone;} public void setPhone(String value){phone=value;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String value){passwordHash=value;}
    public String getFullName(){return fullName;} public void setFullName(String value){fullName=value;}
    public String getAddress(){return address;} public void setAddress(String value){address=value;}
    public boolean isActive(){return active;} public void setActive(boolean value){active=value;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
