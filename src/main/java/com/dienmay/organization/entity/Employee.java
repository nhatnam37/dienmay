package com.dienmay.organization.entity;

import jakarta.persistence.*;

@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Entity
@Table(name = "employee")
public class Employee {

    @org.springframework.data.annotation.CreatedDate
    @Column(name="created_at",nullable=false,updatable=false)
    private java.time.LocalDateTime createdAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: chỉ nạp khi cần, tránh kéo thêm dữ liệu thừa mỗi lần đọc nhân viên.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
