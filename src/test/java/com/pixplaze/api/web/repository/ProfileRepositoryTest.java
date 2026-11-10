package com.pixplaze.api.web.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileRepositoryTest {

    @Test
    void prefixIsLowercased() {
        assertEquals("steve%", ProfileRepository.likePrefix("SteVe"));
    }

    @Test
    void likeWildcardsAreLiteral() {
        assertEquals("a!_b%", ProfileRepository.likePrefix("a_b"), "_ бывает в никах Minecraft");
        assertEquals("100!%%", ProfileRepository.likePrefix("100%"));
        assertEquals("x!!y%", ProfileRepository.likePrefix("x!y"), "символ экранирования тоже экранируется");
    }
}
