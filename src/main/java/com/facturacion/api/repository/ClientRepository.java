package com.facturacion.api.repository;

import com.facturacion.api.model.Client;
import com.facturacion.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByUser(User user);

    Optional<Client> findByIdAndUser(Long id, User user);

    List<Client> findByUserAndNameContainingIgnoreCase(User user, String name);
}
