package com.facturacion.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.facturacion.api.model.Client;
import com.facturacion.api.model.Invoice;
import com.facturacion.api.model.User;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // History of a user's invoices, newest first
    List<Invoice> findByUserOrderByIssueDateDescNumberDesc(User user);

    // Never look up by id alone: the user is part of the query (RF-08)
    Optional<Invoice> findByIdAndUser(Long id, User user);

    // Last number of the year for this user, e.g. prefix "2026-" -> "2026-0007"
    Optional<Invoice> findTopByUserAndNumberStartingWithOrderByNumberDesc(User user, String prefix);

    // Used to forbid deleting clients that already have invoices (RF-07)
    boolean existsByClient(Client client);
}