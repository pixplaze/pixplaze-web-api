package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.dto.FriendOnlineInfo;
import com.pixplaze.api.web.data.dto.ProfileFriendship;
import com.pixplaze.api.web.exception.http.BadRequestException;
import com.pixplaze.api.web.repository.ProfileFriendRepository;
import com.pixplaze.api.web.repository.ProfilePlayerRepository.PlayerHead;
import com.pixplaze.api.web.service.presence.MinecraftPresenceStore;
import com.pixplaze.api.web.service.presence.MinecraftPresenceStore.PlayerPresence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
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
    private final MinecraftPresenceStore minecraftPresenceStore;
    private final MinecraftServerService minecraftServerService;

    /// Короче — слишком много совпадений, поиск превращается в перебор профилей.
    static final int SEARCH_MIN_LENGTH = 2;

    /// Друзья со статусом: взаимные, исходящие (профиль добавил) и входящие (профиль добавили).
    public List<ProfileFriendship> findFriends(Long profileId) {
        return profileFriendRepository.findFriends(profileId)
                .stream()
                .map(FriendService::toProfileFriendship)
                .toList();
    }

    /// Добавляет в друзья по имени профиля. Дружбой это станет, когда добавят в ответ.
    public void add(Long profileId, Long friendProfileId) {
        if (Objects.equals(profileId, friendProfileId)) {
            throw new BadRequestException("Cannot add yourself as a friend");
        }
        profileFriendRepository.add(profileId, friendProfileId);
    }

    /// Убирает друга со своей стороны; его строка («он добавил меня») остаётся.
    public void remove(Long profileId, Long friendProfileId) {
        profileFriendRepository.remove(profileId, friendProfileId);
    }

    /// Профили, чьё имя или ник привязанного игрока начинается с `query` (без учёта регистра), кроме самого
    /// профиля: не больше [#SEARCH_LIMIT], по имени, с головой скина и отношением к искавшему.
    public List<ProfileFriendship> search(Long profileId, String query, int limit, int offset) {
        final var trimmed = query == null ? "" : query.strip();
        if (trimmed.length() < SEARCH_MIN_LENGTH) {
            throw new BadRequestException("Search query must be at least %d characters".formatted(SEARCH_MIN_LENGTH));
        }
        final var found = profileFriendRepository.searchFriends(profileId, trimmed, limit, offset);
        if (found.isEmpty()) return List.of();

        return found.stream()
                .map(FriendService::toProfileFriendship)
                .toList();
    }

    /// Голова игрока, чей ник начинается с запроса, иначе первого по нику; `heads` — по нику, только с головой.
    static String headOf(List<PlayerHead> heads, String query) {
        return heads.stream()
                .filter(head -> head.username().regionMatches(true, 0, query, 0, query.length()))
                .findFirst()
                .or(() -> heads.stream().findFirst())
                .map(PlayerHead::skinHead)
                .orElse(null);
    }

    /// Онлайн самого профиля и всех взаимных друзей: игроки в сети с сервером и время выхода.
    public List<FriendOnlineInfo> findOnline(Long profileId) {
        final var profileIds = new ArrayList<Long>();
        profileIds.add(profileId);
        profileIds.addAll(profileFriendRepository.findMutualIds(profileId));

        final var profiles = profileFriendRepository.findProfilePlayers(profileIds).stream()
                .collect(Collectors.groupingBy(ProfileFriendRepository.ProfileFriendPlayer::id, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(rows -> onlineProfile(rows, rows.get(0).id().equals(profileId)))
                .toList();
        final var servers = serversOf(profiles);
        return profiles.stream()
                .map(profile -> profile.toInfo(servers))
                .toList();
    }

    /// Что смотрящему видно из онлайна профиля: себе — все игроки, другу — кроме ванишей;
    /// «скрывать онлайн» — ни игроков, ни времени выхода.
    private OnlineProfile onlineProfile(List<ProfileFriendRepository.ProfileFriendPlayer> rows, boolean isSelf) {
        final var first = rows.get(0);
        if (first.isOnlineHidden() && !isSelf) {
            return new OnlineProfile(first.id(), first.username(), null, List.of());
        }
        final var lastSeenAt = rows.stream()
                .map(ProfileFriendRepository.ProfileFriendPlayer::lastSeenAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        final var presences = rows.stream()
                .map(ProfileFriendRepository.ProfileFriendPlayer::playerUuid)
                .filter(Objects::nonNull)
                .map(minecraftPresenceStore::find)
                .flatMap(Optional::stream)
                .filter(presence -> isSelf || !Boolean.TRUE.equals(presence.player().isHidden()))
                .toList();
        return new OnlineProfile(first.id(), first.username(), lastSeenAt, presences);
    }

    /// Серверы, на которых сейчас игроки, — одним чтением снапшота, кратко: id, название и адреса.
    private Map<Long, MinecraftServerInfo> serversOf(List<OnlineProfile> profiles) {
        final var ids = profiles.stream()
                .flatMap(profile -> profile.presences().stream())
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

    /// Онлайн профиля, уже отфильтрованный для смотрящего ([#onlineProfile]); живёт один вызов [#findOnline].
    private record OnlineProfile(Long profileId, String name, OffsetDateTime lastSeenAt, List<PlayerPresence> presences) {

        /// Сервер, которого нет в `servers` (web-api его ещё не знает), — только с id.
        FriendOnlineInfo toInfo(Map<Long, MinecraftServerInfo> servers) {
            final var players = presences.stream()
                    .map(presence -> new FriendOnlineInfo.Player(
                            presence.player().uuid(),
                            presence.player().username(),
                            servers.getOrDefault(presence.minecraftServerId(),
                                    MinecraftServerInfo.brief(presence.minecraftServerId(), null, List.of()))))
                    .toList();
            return new FriendOnlineInfo(profileId, name, players, lastSeenAt);
        }
    }

    private static ProfileFriendship toProfileFriendship(ProfileFriendRepository.ProfileFriendPlayer profileFriendPlayer) {
        return new ProfileFriendship(
                profileFriendPlayer.id(),
                profileFriendPlayer.username(),
                profileFriendPlayer.skinHeadBase64(),
                profileFriendPlayer.status()
        );
    }
}
