package com.learnium.learniumbackend.repository.v1.user;


import java.util.Optional;
import java.util.UUID;
import com.learnium.learniumbackend.model.v1.common.AuthProvider;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends BaseRepository<UserAccount> {

    Optional<UserAccount> findByAuthProviderAndAuthUid(AuthProvider authProvider, String authUid);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @Query("""
        select u.userId
        from UserAccount u
        where u.authProvider = :authProvider and u.authUid = :authUid
    """)
    Optional<UUID> findIdByAuth(AuthProvider authProvider, String authUid);

    @Query("""
        select u.userId
        from UserAccount u
        where u.authUid = :authUid
    """)
    Optional<UUID>  findIdByAuthId(String authUid);
}