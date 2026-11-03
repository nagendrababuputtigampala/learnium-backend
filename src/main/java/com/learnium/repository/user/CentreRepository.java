package com.learnium.repository.user;

import com.learnium.model.user.Centre;
import com.learnium.model.user.CentreStatus;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CentreRepository extends JpaRepository<Centre, UUID> {

    boolean existsByName(String name);
    boolean existsByCode(String code);
    Optional<Centre> findByCode(String code);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Centre c where c.centreId = :id")
    Optional<Centre> lockById(UUID id);

    /** Public centre picker: active centres only, alphabetical. */
    List<Centre> findByStatusOrderByNameAsc(CentreStatus status);
}
