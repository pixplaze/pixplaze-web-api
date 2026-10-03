package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.service.auth.device.model.ApproverDecision;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationRequest;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationState;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * Хранилище device-flow (RFC 8628). Ключ — хэш device-кода, вторичный индекс — user-код.
 *
 * <p>Стор владеет всем, что меняется конкурентно: сроком жизни, бюджетом опросов, отметкой опроса,
 * решением подтверждающего и правом на выдачу. Атомарность — свойство операции над общим
 * хранилищем, а не значения в памяти запроса, поэтому каждая такая величина меняется только
 * здесь и только одной операцией.
 *
 * <h2>Инварианты контракта</h2>
 * <ul>
 *   <li><b>Запись жива, пока есть её запрос и не истёк срок.</b> «Записи нет» и «запись истекла»
 *       снаружи неразличимы; истечение проверяется на каждом чтении.</li>
 *   <li><b>Сохранённый запрос не перезаписывается никогда.</b> Меняются только счётчик попыток,
 *       отметка опроса (у неё свой ключ) и решение (своё одноразовое значение). Операции
 *       «прочитать значение — записать новое» в контракте нет.</li>
 *   <li><b>Одноразовость выражается операцией.</b> Решение принимается записью «если ещё нет»,
 *       право на выдачу — удалением, которое удаётся ровно одному вызову.</li>
 *   <li><b>«Записи нет» — это всегда пустой {@link Optional}.</b> Одна кодировка на весь
 *       контракт: ни {@code null} внутри значения, ни отдельной константы исхода.</li>
 *   <li><b>Срок жизни только сокращается.</b> Никакая операция не продлевает запись и не создаёт
 *       её без срока.</li>
 *   <li>Через границу ходят только сериализуемые значения; детали — сырой строкой, их тип
 *       хранилище не знает.</li>
 * </ul>
 *
 * <h2>Раскладка в Redis 8.x</h2>
 * <pre>
 * dfs:{h}   hash   request, details, attempts   TTL = срок сессии, после решения сокращается
 * dfd:{h}   string решение подтверждающего       SET NX, свой TTL
 * dfp:{h}   string отметка опроса                SET NX PX interval
 * dfu:code  string h                             индекс по user-коду, TTL = срок сессии
 * </pre>
 * {@code h} — хэш device-кода. Hash tag {@code {h}} кладёт три ключа сессии в один слот кластера,
 * поэтому любая операция над ними — одна транзакция {@code MULTI}. Индекс по user-коду
 * тегировать нечем, поэтому операции, которые его трогают ({@link #tryCreate},
 * {@link #findByUserCode}), выполняются отдельными командами: так они работают одинаково и
 * с Sentinel, и с кластером. Lua не нужен. Читать только с мастера.
 *
 * <p>{@code HINCRBY} и {@code SET NX} по отсутствующему ключу создают огрызки: хеш с одним полем
 * {@code attempts} или маркер без сессии. Оба получают срок в той же транзакции, а запись без
 * поля {@code request} живой не считается, поэтому огрызки безвредны и исчезают сами.
 */
public interface DeviceAuthorizationStore {

    /**
     * Размещает новый запрос вместе с деталями и бюджетом опросов под общим сроком.
     *
     * <p>Redis: {@code SET dfu:code h NX EX ttl}; при успехе —
     * {@code MULTI { HSET dfs:{h} request … details … attempts …; EXPIRE dfs:{h} ttl }}.
     * Если процесс упадёт между двумя шагами, останется индекс без записи: он читается как
     * «записи нет» и истечёт сам.
     *
     * @param details       полезная нагрузка запроса как её прислало устройство; {@code null}, если её нет
     * @param attemptBudget сколько опросов разрешено за срок жизни
     * @param ttl           срок жизни записи
     * @return {@code false} — user-код уже занят живым запросом; ничего не записано
     */
    boolean tryCreate(DeviceAuthorizationRequest request, @Nullable String details, int attemptBudget, Duration ttl);

    /**
     * Читает запись, списывает попытку и отмечает обращение — одной операцией. Попытка
     * списывается с каждого обращения, включая слишком раннее. Раннее обращение отметку не
     * сдвигает: интервал считается от прошлого принятого (RFC 8628 §3.5).
     *
     * <p>Исход не выводится: наружу отдаются факты — снимок записи и удалось ли поставить
     * отметку. Что они означают для протокола, решает вызывающий, поэтому расходиться
     * реализациям не на чем.
     *
     * @return пусто — записи нет или она истекла
     *
     * <p>Redis, один round-trip: {@code MULTI { HMGET dfs:{h} request details; GET dfd:{h};
     * HINCRBY dfs:{h} attempts -1; EXPIRE dfs:{h} interval NX; SET dfp:{h} 1 NX PX interval }}.
     * {@code EXPIRE … NX} трогает только огрызок без срока и живой записи безразличен.
     */
    Optional<ConsumedAttempt> readAndConsumeAttempt(String deviceCodeHash, Duration interval);

    /**
     * Поиск по user-коду — путь подтверждающего.
     *
     * <p>Redis: {@code GET dfu:code}, затем
     * {@code MULTI { HMGET dfs:{h} request details attempts; GET dfd:{h} }}.
     */
    Optional<DeviceAuthorizationState> findByUserCode(String userCode);

    /**
     * Записывает решение подтверждающего, если его ещё нет, и сокращает срок жизни записи до
     * {@code ttl}. Решение не перезаписывается никогда, в том числе тем же подтверждающим.
     *
     * <p>Redis, один round-trip: {@code MULTI { HEXISTS dfs:{h} request;
     * SET dfd:{h} <json> NX EX ttl; EXPIRE dfs:{h} ttl LT }}. {@code LT} только сокращает срок,
     * поэтому одобрение за секунды до истечения запись не продлевает.
     *
     * <p>Срок сокращается и тогда, когда решение уже было принято раньше: {@code MULTI} не
     * ветвится. Вреда в этом нет: окно после решения у всех решений одинаковое, так что
     * проигравший лишь повторно применяет уже действующее ограничение.
     *
     * @return пусто — записи нет или она истекла; решение никуда не записано
     */
    Optional<DecisionOutcome> putDecisionIfAbsent(String deviceCodeHash, ApproverDecision decision, Duration ttl);

    /**
     * Отдаёт снимок записи вместе с деталями и удаляет её. Из параллельных вызовов снимок
     * получает ровно один, остальные — пустой результат: на этом держится одноразовость права
     * на выдачу токенов.
     *
     * <p>Статус здесь не проверяется — это дело вызывающего. Решение одноразово, поэтому между
     * чтением записи и её изъятием статус измениться не может.
     *
     * <p>Redis: {@code MULTI { HMGET dfs:{h} request details attempts; GET dfd:{h};
     * DEL dfs:{h}; DEL dfd:{h} dfp:{h} }}. Выиграл тот, у кого первый {@code DEL} вернул 1.
     */
    Optional<DeviceAuthorizationState> takeAndRemove(String deviceCodeHash);

    /**
     * Удаляет запись. Отсутствие записи ошибкой не считается.
     *
     * <p>Redis: {@code DEL dfs:{h} dfd:{h} dfp:{h}}. Индекс по user-коду не удаляется: он
     * истечёт сам, а разыменование без записи читается как «записи нет».
     */
    void remove(String deviceCodeHash);

    /**
     * Факты живой записи, прочитанные и записанные одним {@link #readAndConsumeAttempt}. Что они
     * означают для протокола, выводит вызывающий: приоритет причин отказа — его решение, а не
     * свойство хранилища.
     *
     * @param state          снимок записи. {@code attemptsLeft} здесь уже за вычетом текущего
     *                       обращения, поэтому отрицательное значение значит «вышли за бюджет»
     * @param isTooSoon      обращение пришло раньше, чем прошёл интервал с прошлого принятого,
     *                       то есть отметку обращения поставить не удалось
     */
    record ConsumedAttempt(DeviceAuthorizationState state, boolean isTooSoon) {}

    /// Исход условной записи решения по живой записи. «Записи нет» сюда не входит — это пустой
    /// {@link Optional} у {@link #putDecisionIfAbsent}.
    enum DecisionOutcome {
        DECIDED,
        /// Решение уже было принято; сохранённое не изменилось.
        ALREADY_DECIDED
    }
}
