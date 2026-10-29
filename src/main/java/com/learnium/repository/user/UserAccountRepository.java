package com.learnium.repository.user;

import com.learnium.model.common.UserStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * Status-only projection for the per-request authentication check: the
     * JWT filter loads nothing but the account's current status by id.
     */
    @Query("select u.status from UserAccount u where u.userId = :userId")
    Optional<UserStatus> findStatusByUserId(@Param("userId") UUID userId);

    /** Centre-scoped: only ever returns users belonging to the given centre. */
    @Query("select u from UserAccount u where u.centre.centreId = :centreId")
    List<UserAccount> findByCentreId(@Param("centreId") UUID centreId);

    /** Centre-scoped single-user read for tenant aggregates (never a bare findById). */
    @Query(
            "select u from UserAccount u"
                    + " where u.centre.centreId = :centreId and u.userId = :userId")
    Optional<UserAccount> findByCentreIdAndUserId(
            @Param("centreId") UUID centreId, @Param("userId") UUID userId);

    /** Centre-scoped, role-filtered page (admin teacher list). */
    @Query(
            "select u from UserAccount u"
                    + " where u.centre.centreId = :centreId and u.role = :role")
    Page<UserAccount> findByCentreIdAndRole(
            @Param("centreId") UUID centreId, @Param("role") UserRole role, Pageable pageable);

    /**
     * Global staff lookup by email, used at the login door and at account
     * creation (staff emails are globally unique — enforced where accounts are
     * created). Returns every account with that email across centres; callers
     * decide what a match means.
     */
    @Query("select u from UserAccount u where u.email = :email")
    List<UserAccount> findByEmail(@Param("email") String email);

    @Query(
            "select case when count(u) > 0 then true else false end"
                    + " from UserAccount u where u.email = :email")
    boolean existsByEmail(@Param("email") String email);
}
