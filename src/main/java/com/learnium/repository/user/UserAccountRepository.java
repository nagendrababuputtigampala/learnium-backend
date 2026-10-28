package com.learnium.repository.user;

import com.learnium.model.user.UserAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** Centre-scoped: only ever returns users belonging to the given centre. */
    @Query("select u from UserAccount u where u.centre.centreId = :centreId")
    List<UserAccount> findByCentreId(@Param("centreId") UUID centreId);
}
