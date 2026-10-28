package com.learnium.repository.user;

import com.learnium.model.user.Centre;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentreRepository extends JpaRepository<Centre, UUID> {

    boolean existsByName(String name);
}
