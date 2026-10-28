package com.learnium.repository.user;

import com.learnium.model.user.Centre;
import com.learnium.model.user.CentreStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentreRepository extends JpaRepository<Centre, UUID> {

    boolean existsByName(String name);

    /** Public centre picker: active centres only, alphabetical. */
    List<Centre> findByStatusOrderByNameAsc(CentreStatus status);
}
