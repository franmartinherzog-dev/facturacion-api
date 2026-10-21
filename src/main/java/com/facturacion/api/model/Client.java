package com.facturacion.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clients")
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "tax_id", nullable = false, length = 20)
    private String taxId;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    // Owner of this client: each user only sees their own clients
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Client() { } // Required by JPA

    public Client(String name, String taxId, String address, String email, String phone, User user) {
        this.name = name;
        this.taxId = taxId;
        this.address = address;
        this.email = email;
        this.phone = phone;
        this.user = user;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getTaxId() { return taxId; }
    public String getAddress() { return address; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public User getUser() { return user; }

    public void setName(String name) { this.name = name; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public void setAddress(String address) { this.address = address; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
}