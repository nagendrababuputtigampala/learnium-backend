package com.learnium.learniumbackend.repository.v1.gamification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.gamification.UserXpLedger;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserXpLedgerRepository extends BaseRepository<UserXpLedger> {

    @Query("""
        select coalesce(sum(x.xpDelta), 0)
        from UserXpLedger x
        where x.user.userId = :userId
    """)
    int getTotalXp(UUID userId);

    @Query("""
        select coalesce(sum(x.xpDelta), 0)
        from UserXpLedger x
        where x.user.userId = :userId
          and x.earnedAt >= :from
    """)
    int getXpSince(UUID userId, Instant from);

    List<UserXpLedger> findByUser_UserIdOrderByEarnedAtDesc(UUID userId);
}