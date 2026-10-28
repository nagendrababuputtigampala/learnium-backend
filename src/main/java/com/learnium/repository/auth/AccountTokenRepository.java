package com.learnium.repository.auth;

import com.learnium.model.auth.AccountToken;
import com.learnium.model.auth.AccountTokenPurpose;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    /** The token hash is the credential: lookup is by hash + purpose, never by raw token. */
    @Query(
            "select t from AccountToken t"
                    + " where t.tokenHash = :tokenHash and t.purpose = :purpose")
    Optional<AccountToken> findByTokenHashAndPurpose(
            @Param("tokenHash") String tokenHash, @Param("purpose") AccountTokenPurpose purpose);
}
