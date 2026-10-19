package com.pixplaze.api.web.data.dto;

import java.util.Map;
import java.util.Set;

/**
 * Подсказка с правами для интерфейса: только чтобы прятать и показывать экраны, решения принимает
 * целевое приложение.
 *
 * @param system  системные права профиля (по ролям его токена)
 * @param servers игровые права по id сервера — на каждом незабаненном сервере, где у профиля есть роль
 *                (участник, оператор, владелец); сервера без ролей в карте нет
 */
public record PermissionsHint(Set<String> system, Map<Long, Set<String>> servers) {

    public static final PermissionsHint EMPTY = new PermissionsHint(Set.of(), Map.of());
}
