package com.learnium.repository.gamification;

import com.learnium.model.gamification.UserXpLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserXpLedgerRepository extends JpaRepository<UserXpLedger, UUID> {

    @Query("select x from UserXpLedger x where x.user.userId = :userId")
    List<UserXpLedger> findByUserId(@Param("userId") UUID userId);
}
