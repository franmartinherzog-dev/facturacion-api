package com.facturacion.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users") // "user" is a reserved word in H2
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // Stores the BCrypt hash, never the plain-text password
    @Column(nullable = false)
    private String password;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "tax_id", nullable = false, length = 20)
    private String taxId;

    @Column(length = 255)
    private String address;

    protected User() { } // Required by JPA

    public User(String username, String password, String companyName, String taxId, String address) {
        this.username = username;
        this.password = password;
        this.companyName = companyName;
        this.taxId = taxId;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getCompanyName() { return companyName; }
    public String getTaxId() { return taxId; }
    public String getAddress() { return address; }
}
