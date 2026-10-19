package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.dto.FriendInfo;
import com.pixplaze.api.web.data.dto.FriendOnlineInfo;
import com.pixplaze.api.web.exception.http.BadRequestException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.repository.ProfileFriendRepository;
import com.pixplaze.api.web.repository.ProfileFriendRepository.ProfilePlayerRow;
import com.pixplaze.api.web.repository.ProfileRepository;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.service.presence.MinecraftPresenceStore;
import com.pixplaze.api.web.service.presence.MinecraftPresenceStore.PlayerPresence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Друзья профиля и их онлайн. Дружба — взаимное добавление; онлайн видят только взаимные друзья.
 * Профиль всегда видит своих игроков. Друг со «скрывать онлайн» виден без игроков и без времени
 * выхода; игрок в ванише (флаг «скрыт» из снимка heartbeat) не показывается друзьям.
 */
@Service
@RequiredArgsConstructor
public class FriendService {

    private final ProfileFriendRepository profileFriendRepository;
    private final ProfileRepository profileRepository;
    private final MinecraftPresenceStore minecraftPresenceStore;
    private final MinecraftServerService minecraftServerService;

    /// Друзья со статусом: взаимные, исходящие (профиль добавил) и входящие (профиль добавили).
    public List<FriendInfo> findFriends(Long profileId) {
        final var incoming = profileFriendRepository.findIncoming(profileId);
        final var incomingIds = new HashSet<Long>();
        incoming.forEach(ref -> incomingIds.add(ref.id()));

        final var friends = new LinkedHashMap<Long, FriendInfo>();
        profileFriendRepository.findOutgoing(profileId).forEach(ref -> friends.put(ref.id(), new FriendInfo(ref.id(), ref.name(),
                incomingIds.contains(ref.id()) ? FriendInfo.Status.MUTUAL : FriendInfo.Status.OUTGOING)));
        incoming.forEach(ref -> friends.putIfAbsent(ref.id(), new FriendInfo(ref.id(), ref.name(), FriendInfo.Status.INCOMING)));
        return List.copyOf(friends.values());
    }

    /// Добавляет в друзья по имени профиля. Дружбой это станет, когда добавят в ответ.
    public void add(Long profileId, String friendName) {
        final var friend = profileRepository.findByUsername(friendName)
                .orElseThrow(() -> new NotFoundException("Profile '%s' not found".formatted(friendName)));
        if (Objects.equals(friend.getId(), profileId)) {
            throw new BadRequestException("Cannot add yourself as a friend");
        }
        profileFriendRepository.add(profileId, friend.getId());
    }

    /// Убирает друга со своей стороны; его строка («он добавил меня») остаётся.
    public void remove(Long profileId, Long friendProfileId) {
        profileFriendRepository.remove(profileId, friendProfileId);
    }

    /// Онлайн самого профиля и всех взаимных друзей: игроки в сети с сервером и время выхода.
    public List<FriendOnlineInfo> findOnline(Long profileId) {
        final var profileIds = new ArrayList<Long>();
        profileIds.add(profileId);
        profileIds.addAll(profileFriendRepository.findMutualIds(profileId));

        final var rowsByProfile = new LinkedHashMap<Long, List<ProfilePlayerRow>>();
        profileFriendRepository.findProfilePlayers(profileIds)
                .forEach(row -> rowsByProfile.computeIfAbsent(row.profileId(), id -> new ArrayList<>()).add(row));

        final var presences = new LinkedHashMap<Long, List<PlayerPresence>>();
        rowsByProfile.forEach((id, rows) -> presences.put(id, visiblePresences(rows, id.equals(profileId))));
        final var servers = serversOf(presences.values());

        final var result = new ArrayList<FriendOnlineInfo>();
        rowsByProfile.forEach((id, rows) -> result.add(toOnlineInfo(rows, presences.get(id), servers, id.equals(profileId))));
        return result;
    }

    /// Игроки профиля в сети, которых видно смотрящему: себе — все, другу — кроме ванишей; «скрывать онлайн» — никого.
    private List<PlayerPresence> visiblePresences(List<ProfilePlayerRow> rows, boolean isSelf) {
        if (rows.get(0).isOnlineHidden() && !isSelf) {
            return List.of();
        }
        return rows.stream()
                .map(ProfilePlayerRow::playerUuid)
                .filter(Objects::nonNull)
                .map(minecraftPresenceStore::find)
                .flatMap(Optional::stream)
                .filter(presence -> isSelf || !Boolean.TRUE.equals(presence.player().isHidden()))
                .toList();
    }

    /// Серверы, на которых сейчас игроки, — одним чтением снапшота, кратко: id, название и адреса.
    private Map<Long, MinecraftServerInfo> serversOf(Collection<List<PlayerPresence>> presences) {
        final var ids = presences.stream()
                .flatMap(List::stream)
                .map(PlayerPresence::minecraftServerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        final var servers = new HashMap<Long, MinecraftServerInfo>();
        minecraftServerService.getServerInfos(ids).forEach(info ->
                servers.put(info.id(), MinecraftServerInfo.brief(info.id(), info.name(), info.hosts())));
        return servers;
    }

    private FriendOnlineInfo toOnlineInfo(List<ProfilePlayerRow> rows, List<PlayerPresence> presences,
                                          Map<Long, MinecraftServerInfo> servers, boolean isSelf) {
        final var first = rows.get(0);
        if (first.isOnlineHidden() && !isSelf) {
            return new FriendOnlineInfo(first.profileId(), first.profileName(), List.of(), null);
        }

        final var lastSeenAt = rows.stream()
                .map(ProfilePlayerRow::lastSeenAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        final var players = presences.stream()
                .map(presence -> new FriendOnlineInfo.Player(presence.player().uuid(), presence.player().username(),
                        servers.getOrDefault(presence.minecraftServerId(),
                                MinecraftServerInfo.brief(presence.minecraftServerId(), null, List.of()))))
                .toList();
        return new FriendOnlineInfo(first.profileId(), first.profileName(), players, lastSeenAt);
    }
}
