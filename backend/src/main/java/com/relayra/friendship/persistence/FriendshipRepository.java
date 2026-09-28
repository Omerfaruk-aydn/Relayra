package com.relayra.friendship.persistence;

import com.relayra.friendship.domain.Friendship;
import com.relayra.friendship.domain.FriendshipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

  List<Friendship> findBySenderIdAndStatus(UUID senderId, FriendshipStatus status);

  List<Friendship> findByReceiverIdAndStatus(UUID receiverId, FriendshipStatus status);

  @Query(
      """
      select f from Friendship f
      where f.userLowId = :low and f.userHighId = :high
        and f.status = com.relayra.friendship.domain.FriendshipStatus.PENDING
      """)
  Optional<Friendship> findPendingBetween(
      @Param("low") UUID low, @Param("high") UUID high);

  @Query(
      """
      select f from Friendship f
      where ((f.senderId = :user and f.receiverId = :other)
          or (f.senderId = :other and f.receiverId = :user))
        and f.status = com.relayra.friendship.domain.FriendshipStatus.ACCEPTED
      """)
  Optional<Friendship> findAcceptedBetween(
      @Param("user") UUID user, @Param("other") UUID other);

  @Query(
      """
      select f from Friendship f
      where (f.senderId = :user or f.receiverId = :user)
        and f.status = com.relayra.friendship.domain.FriendshipStatus.ACCEPTED
      """)
  List<Friendship> findAcceptedForUser(@Param("user") UUID user);
}
