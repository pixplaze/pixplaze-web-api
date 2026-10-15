package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.db.tables.pojos.ProfileFriend;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Select;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static com.pixplaze.api.web.data.db.tables.ProfileFriendTable.PROFILE_FRIEND;
import static com.pixplaze.api.web.data.db.tables.ProfileTable.PROFILE;

@Repository
@AllArgsConstructor
public class ProfileFriendRepository {
  private DSLContext dslContext;

  //  add(profileId, friendId): ON CONFLICT DO NOTHING, повторный вызов не должен падать;
  public ProfileFriend add(long profileId, long friendId) {
    final var friendRequest = dslContext.insertInto(PROFILE_FRIEND)
        .set(PROFILE_FRIEND.PROFILE_ID, profileId)
        .set(PROFILE_FRIEND.FRIEND_PROFILE_ID, friendId)
        .returning(PROFILE_FRIEND)
        .fetchOneInto(ProfileFriend.class);
    return friendRequest;
  }

  //  remove(profileId, friendId);
  public void delete(long profileId, long friendId) {
    dslContext.delete(PROFILE_FRIEND)
        .where(PROFILE_FRIEND.PROFILE_ID.eq(profileId))
        .and(PROFILE_FRIEND.FRIEND_PROFILE_ID.eq(friendId))
        .execute();
  }

  //  findFriends(profileId), findIncoming(profileId), findOutgoing(profileId):
  public List<Profile> findFriendsProfiles(long profileId) {
    final var f1 = PROFILE_FRIEND.as("f1");
    final var f2 = PROFILE_FRIEND.as("f2");

    Select<?> subQFriends = dslContext
        .select(f1.FRIEND_PROFILE_ID)
        .from(f1)
        .innerJoin(f2)
        .on(f1.PROFILE_ID.eq(f2.FRIEND_PROFILE_ID))
        .and(f2.PROFILE_ID.eq(f1.FRIEND_PROFILE_ID))
        .where(f1.PROFILE_ID.eq(profileId));

    return dslContext
        .select()
        .from(PROFILE)
        .join(subQFriends)
        .on(PROFILE.ID.eq(subQFriends.field(PROFILE_FRIEND.FRIEND_PROFILE_ID)))
        .fetchInto(Profile.class);
  }

  public List<ProfileFriend> findFriendsProfilesRequestsIncoming(long profileId) {
    final var f1 = PROFILE_FRIEND.as("f1");
    final var f2 = PROFILE_FRIEND.as("f2");

    return dslContext
        .select(f1.asterisk())
        .from(f1)
        .leftJoin(f2)
        .on(f1.FRIEND_PROFILE_ID.eq(f2.PROFILE_ID))
        .and(f1.PROFILE_ID.eq(f2.FRIEND_PROFILE_ID))
        .where(f1.FRIEND_PROFILE_ID.eq(profileId))
        .and(f2.PROFILE_ID.isNull())
        .fetchInto(ProfileFriend.class);
  }

  public List<ProfileFriend> findFriendsProfilesRequestsOutgoing(long profileId) {
    final var f1 = PROFILE_FRIEND.as("f1");
    final var f2 = PROFILE_FRIEND.as("f2");

    return dslContext
        .select(f1.asterisk())
        .from(f1)
        .leftJoin(f2)
        .on(f1.FRIEND_PROFILE_ID.eq(f2.PROFILE_ID))
        .and(f1.PROFILE_ID.eq(f2.FRIEND_PROFILE_ID))
        .where(f1.PROFILE_ID.eq(profileId))
        .and(f2.PROFILE_ID.isNull())
        .fetchInto(ProfileFriend.class);
  }

  public List<ProfileFriend> findFriends(long profileId) {

    return null;
  }

  public List<ProfileFriend> findIncoming(long profileId) {

    return null;
  }

  public List<ProfileFriend> findOutgoing(long profileId) {

    return null;
  }

  //  filterVisibleProfiles(requesterId, Collection<Long> profileIds): из набора оставить взаимных друзей запрашивающего
  public List<ProfileFriend> filterVisibleProfiles(long requesterId, Collection<Long> profileIds) {

    return null;
  }

}
