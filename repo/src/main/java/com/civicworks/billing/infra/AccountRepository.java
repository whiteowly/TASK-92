package com.civicworks.billing.infra;

import com.civicworks.billing.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByStatus(String status);

    java.util.Optional<Account> findByResidentIdHash(String residentIdHash);
}
