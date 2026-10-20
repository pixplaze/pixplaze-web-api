package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.db.tables.pojos.ProfileFriend;
import com.pixplaze.api.web.repository.ProfileFriendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileFriendService {

  private final ProfileFriendRepository profileFriendRepository;

  public List<Profile> findFriendsProfiles(Long id) {
    return profileFriendRepository.findFriendsProfiles(id);
  }

  public List<ProfileFriend> findFriendsProfilesRequestsIncoming(Long id) {
    return profileFriendRepository.findFriendsProfilesRequestsIncoming(id);
  }

  public List<ProfileFriend> findFriendsProfilesRequestsOutgoing(Long id) {
    return profileFriendRepository.findFriendsProfilesRequestsOutgoing(id);
  }

  public ProfileFriend add(long profileId, long friendId) {
    return profileFriendRepository.add(profileId, friendId);
  }

  public void remove(long profileId, long friendId) {
    profileFriendRepository.delete(profileId, friendId);
  }

}
