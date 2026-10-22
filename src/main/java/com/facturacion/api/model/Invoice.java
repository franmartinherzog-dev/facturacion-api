package com.facturacion.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity 
@Table(name = "invoices",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "number"}))
public class Invoice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Format YYYY-NNNN (for example 2026-0001), sequential per user
    @Column(nullable = false, length = 12)
    private String number;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "irpf_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal irpfRate;

    // The amounts below are always calculated by the API, never taken from the client
    @Column(name = "taxable_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableBase;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "irpf_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal irpfAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    protected Invoice() { } // Required by JPA

    public Invoice(String number, LocalDate issueDate, User user, Client client,
        BigDecimal vatRate, BigDecimal irpfRate, BigDecimal taxableBase,
        BigDecimal vatAmount, BigDecimal irpfAmount, BigDecimal total) {
        this.number = number;
        this.issueDate = issueDate;
        this.user = user;
        this.client = client;
        this.vatRate = vatRate;
        this.irpfRate = irpfRate;
        this.taxableBase = taxableBase;
        this.vatAmount = vatAmount;
        this.irpfAmount = irpfAmount;
        this.total = total;
        }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public LocalDate getIssueDate() { return issueDate; }
    public User getUser() { return user; }
    public Client getClient() { return client; }
    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getIrpfRate() { return irpfRate; }
    public BigDecimal getTaxableBase() { return taxableBase; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getIrpfAmount() { return irpfAmount; }
    public BigDecimal getTotal() { return total; }
}