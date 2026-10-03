package com.pixplaze.api.web.service.auth.device.model;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationDetails;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationStatus;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationStrategyInvoker;
import org.jspecify.annotations.Nullable;

/**
 * Всё, что нужно стратегии для обработки одного запроса device-flow.
 *
 * <p>Контекст — противоположность {@link DeviceAuthorizationRequest}: запрос хранится и потому
 * плоский и сериализуемый, а контекст живёт один запрос и потому держит разобранные объекты.
 * Каждое поле производно от хранимого: привилегия восстановлена из {@code scope}, детали разобраны
 * стратегией, статус выведен из наличия решения, одобряющий загружен по {@code approverProfileId}.
 * Собирает всё это {@link DeviceAuthorizationStrategyInvoker}.
 *
 * <p>Контекст не повторяет раскладку хранения намеренно: в хеше лежит ещё и счётчик попыток, но
 * ему здесь не место. Его читает только флоу-сервис, и только через атомарную операцию стора —
 * снимок счётчика в объекте на один запрос был бы бесполезен. По той же причине здесь нет ни
 * самого запроса, ни решения: стратегиям нужны не они, а разобранные значения ниже.
 *
 * <p>Ограничение на состав: сюда попадает только то, что нужно минимум двум местам. Контекст
 * запроса легко превращается в свалку, после чего стратегии начинают зависеть от полей, которых
 * не используют.
 *
 * @param authority запрошенная привилегия, восстановленная из {@code scope}
 * @param details   разобранная полезная нагрузка запроса
 * @param status    стадия флоу: {@code PENDING}, пока решения подтверждающего нет
 * @param approver  одобривший профиль. Заполнен **только** на этапе {@code authorize}; в
 *                  {@code describe} и {@code validate} решения ещё нет и одобряющего не существует.
 */
public record DeviceAuthorizationContext<A extends AuthorizationDetails>(
        Authority authority,
        A details,
        DeviceAuthorizationStatus status,
        @Nullable ApplicationClientPrincipal approver
) {}
