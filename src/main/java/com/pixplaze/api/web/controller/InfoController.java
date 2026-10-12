package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.VersionInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/// Служебная информация о приложении. Версия сборки ({@code /version}) берётся из {@code git.properties}
/// и {@code META-INF/build-info.properties}, их пишут плагины Maven (см. pom).
@RestController
@RequestMapping("/info")
public class InfoController {
    private final VersionInfo versionInfo;

    public InfoController(ObjectProvider<GitProperties> git, ObjectProvider<BuildProperties> build) {
        final var gitProperties = git.getIfAvailable();
        final var buildProperties = build.getIfAvailable();
        this.versionInfo = new VersionInfo(
                gitProperties == null ? null : gitProperties.getShortCommitId(),
                gitProperties == null ? null : firstTag(gitProperties.get("tags")),
                buildProperties == null ? null : buildProperties.getVersion(),
                buildProperties == null ? null : buildProperties.getTime()
        );
    }

    @GetMapping("/version")
    public VersionInfo getVersion() {
        return versionInfo;
    }

    /// {@code git.tags} — теги на HEAD через запятую, пустая строка, если их нет.
    private static String firstTag(String tags) {
        if (tags == null) return null;
        return Arrays.stream(tags.split(","))
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .findFirst()
                .orElse(null);
    }
}
