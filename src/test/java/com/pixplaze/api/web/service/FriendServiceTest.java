package com.pixplaze.api.web.service;

import com.pixplaze.api.web.repository.ProfilePlayerRepository.PlayerHead;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FriendServiceTest {

    private static final List<PlayerHead> HEADS = List.of(
            new PlayerHead(1L, "Alex", "alex-head"),
            new PlayerHead(1L, "Steve", "steve-head"));

    @Test
    void headOfPlayerMatchedByNickname() {
        assertEquals("steve-head", FriendService.headOf(HEADS, "ste"), "без учёта регистра");
    }

    @Test
    void headOfFirstPlayerWhenProfileNameMatched() {
        assertEquals("alex-head", FriendService.headOf(HEADS, "Profile"));
    }

    @Test
    void noHeadWithoutPlayersWithSkins() {
        assertNull(FriendService.headOf(List.of(), "ste"));
    }
}
