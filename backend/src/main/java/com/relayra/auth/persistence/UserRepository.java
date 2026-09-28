package com.relayra.auth.persistence;

import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.user.dto.UserSearchResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsernameNormalized(String usernameNormalized);

  Optional<User> findByEmailNormalized(String emailNormalized);

  boolean existsByUsernameNormalized(String usernameNormalized);

  boolean existsByEmailNormalized(String emailNormalized);

  @Query(
      """
      select new com.relayra.user.dto.UserSearchResult(
        u.id, u.username, coalesce(p.displayName, u.username))
      from User u left join Profile p on p.userId = u.id
      where u.status = :status
        and u.usernameNormalized like concat('%', :query, '%') escape '\\'
      order by u.usernameNormalized asc
      """)
  List<UserSearchResult> searchActiveByUsernameFragment(
      @Param("status") UserStatus status, @Param("query") String query, Pageable pageable);
}
