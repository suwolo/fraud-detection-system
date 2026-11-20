package com.example.txn.repository;

import com.example.txn.dto.FlaggedAccount;
import com.example.txn.dto.PageResponse;
import com.example.txn.dto.PrevTx;
import com.example.txn.dto.RiskyMerchant;
import com.example.txn.dto.RuleResult;
import com.example.txn.dto.TransactionResponse;
import com.example.txn.dto.TransactionStatus;
import com.example.txn.dto.Tx;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** All native SQL access for transactions. Every value is a bound named parameter. */
@Repository
public class TransactionRepository {

    private static final String TX_COLUMNS =
            "id, account_id, amount, currency, merchant_id, merchant_category, country, event_time";

    private static final String RESPONSE_COLUMNS = TX_COLUMNS + ", status, risk_score, rule_results::text AS rule_results";

    private static final TypeReference<List<RuleResult>> RULE_LIST = new TypeReference<List<RuleResult>>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TransactionRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private static final RowMapper<Tx> TX_MAPPER = (rs, i) -> mapTx(rs);

    private static Tx mapTx(ResultSet rs) throws SQLException {
        return new Tx(
                rs.getString("id"),
                rs.getString("account_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getString("merchant_id"),
                rs.getString("merchant_category"),
                rs.getString("country").trim(),
                rs.getObject("event_time", OffsetDateTime.class).toInstant());
    }

    private RowMapper<TransactionResponse> responseMapper() {
        return (rs, i) -> {
            Tx t = mapTx(rs);
            int score = rs.getInt("risk_score");
            Integer riskScore = rs.wasNull() ? null : score;
            return new TransactionResponse(t.id(), t.accountId(), t.amount(), t.currency(), t.merchantId(),
                    t.merchantCategory(), t.country(), t.eventTime(),
                    TransactionStatus.valueOf(rs.getString("status")), riskScore,
                    fromJson(rs.getString("rule_results")));
        };
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    // ---- JSON helpers ----

    private String toJson(List<RuleResult> results) {
        if (results == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize rule results", e);
        }
    }

    private List<RuleResult> fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RULE_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot parse rule results", e);
        }
    }

    // ---- Basic operations ----

    public boolean accountExists(String accountId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM accounts WHERE id = :accountId)",
                new MapSqlParameterSource("accountId", accountId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /** Inserts the transaction with status RECEIVED and no score. */
    public void insert(Tx tx) {
        jdbc.update("INSERT INTO transactions (" + TX_COLUMNS + ", status, risk_score, rule_results) "
                        + "VALUES (:id, :accountId, :amount, :currency, :merchantId, :merchantCategory, "
                        + ":country, :eventTime, 'RECEIVED', NULL, CAST(:ruleResults AS jsonb))",
                new MapSqlParameterSource()
                        .addValue("id", tx.id())
                        .addValue("accountId", tx.accountId())
                        .addValue("amount", tx.amount())
                        .addValue("currency", tx.currency())
                        .addValue("merchantId", tx.merchantId())
                        .addValue("merchantCategory", tx.merchantCategory())
                        .addValue("country", tx.country())
                        .addValue("eventTime", utc(tx.eventTime()))
                        .addValue("ruleResults", null, java.sql.Types.VARCHAR));
    }

    public void updateDecision(String id, TransactionStatus status, int riskScore, List<RuleResult> ruleResults) {
        jdbc.update("UPDATE transactions SET status = :status, risk_score = :riskScore, "
                        + "rule_results = CAST(:ruleResults AS jsonb) WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("riskScore", riskScore)
                        .addValue("ruleResults", toJson(ruleResults)));
    }

    public Optional<TransactionResponse> findById(String id) {
        List<TransactionResponse> rows = jdbc.query(
                "SELECT " + RESPONSE_COLUMNS + " FROM transactions WHERE id = :id",
                new MapSqlParameterSource("id", id), responseMapper());
        return rows.stream().findFirst();
    }

    /** Paged list, newest event time first; accountId and status filters are optional. */
    public PageResponse<TransactionResponse> findPage(String accountId, TransactionStatus status,
                                                      int page, int size) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conditions = new ArrayList<>();
        if (accountId != null) {
            conditions.add("account_id = :accountId");
            params.addValue("accountId", accountId);
        }
        if (status != null) {
            conditions.add("status = :status");
            params.addValue("status", status.name());
        }
        if (!conditions.isEmpty()) {
            where.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM transactions" + where, params, Long.class);
        long totalElements = total == null ? 0 : total;

        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);
        List<TransactionResponse> content = jdbc.query(
                "SELECT " + RESPONSE_COLUMNS + " FROM transactions" + where
                        + " ORDER BY event_time DESC, id LIMIT :limit OFFSET :offset",
                params, responseMapper());
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    /** Statistics source rows for an account, event time between from and to (inclusive). */
    public List<Tx> findForStatistics(String accountId, Instant from, Instant to) {
        return jdbc.query("SELECT " + TX_COLUMNS + " FROM transactions "
                        + "WHERE account_id = :accountId AND event_time BETWEEN :from AND :to "
                        + "ORDER BY event_time",
                new MapSqlParameterSource()
                        .addValue("accountId", accountId)
                        .addValue("from", utc(from))
                        .addValue("to", utc(to)),
                TX_MAPPER);
    }

    // ---- Native queries 1-4 ----

    /** Query 1: transactions of the account in the window strictly before refTime. */
    public List<Tx> findRecent(String accountId, Instant refTime, int windowMinutes, String txId) {
        return jdbc.query("SELECT " + TX_COLUMNS + " FROM transactions "
                        + "WHERE account_id = :accountId "
                        + "AND id <> :txId "
                        + "AND event_time >= :refTime - make_interval(mins => :windowMinutes) "
                        + "AND event_time < :refTime "
                        + "ORDER BY event_time DESC",
                new MapSqlParameterSource()
                        .addValue("accountId", accountId)
                        .addValue("txId", txId)
                        .addValue("refTime", utc(refTime))
                        .addValue("windowMinutes", windowMinutes),
                TX_MAPPER);
    }

    /** Query 2: accounts with more than minCount flagged transactions. */
    public List<FlaggedAccount> findFlaggedAccounts(int minCount) {
        return jdbc.query("SELECT account_id, COUNT(*) AS flagged_count "
                        + "FROM transactions "
                        + "WHERE status = 'FLAGGED' "
                        + "GROUP BY account_id "
                        + "HAVING COUNT(*) > :minCount "
                        + "ORDER BY flagged_count DESC, account_id",
                new MapSqlParameterSource("minCount", minCount),
                (rs, i) -> new FlaggedAccount(rs.getString("account_id"), rs.getLong("flagged_count")));
    }

    /** Query 3: previous transaction of the same account using LAG; empty if none. */
    public Optional<PrevTx> findPrevious(String accountId, String txId) {
        List<PrevTx> rows = jdbc.query("SELECT prev_time, prev_country FROM ("
                        + "SELECT id, "
                        + "LAG(event_time) OVER w AS prev_time, "
                        + "LAG(country) OVER w AS prev_country "
                        + "FROM transactions "
                        + "WHERE account_id = :accountId "
                        + "WINDOW w AS (PARTITION BY account_id ORDER BY event_time, id)"
                        + ") t WHERE id = :txId",
                new MapSqlParameterSource()
                        .addValue("accountId", accountId)
                        .addValue("txId", txId),
                (rs, i) -> {
                    OffsetDateTime time = rs.getObject("prev_time", OffsetDateTime.class);
                    String country = rs.getString("prev_country");
                    return time == null ? null
                            : new PrevTx(time.toInstant(), country == null ? null : country.trim());
                });
        return rows.stream().filter(p -> p != null).findFirst();
    }

    /** Query 4: top 10 merchants by flagged count, end date inclusive. */
    public List<RiskyMerchant> findRiskyMerchants(LocalDate fromDate, LocalDate toDate) {
        return jdbc.query("SELECT merchant_id, COUNT(*) AS flagged_count, "
                        + "ROUND(AVG(risk_score), 2) AS avg_risk_score "
                        + "FROM transactions "
                        + "WHERE status = 'FLAGGED' "
                        + "AND event_time >= :fromDate "
                        + "AND event_time < :toDateExclusive "
                        + "GROUP BY merchant_id "
                        + "ORDER BY flagged_count DESC, avg_risk_score DESC, merchant_id ASC "
                        + "LIMIT 10",
                new MapSqlParameterSource()
                        .addValue("fromDate", fromDate.atStartOfDay().atOffset(ZoneOffset.UTC))
                        .addValue("toDateExclusive", toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)),
                (rs, i) -> new RiskyMerchant(rs.getString("merchant_id"), rs.getLong("flagged_count"),
                        rs.getBigDecimal("avg_risk_score")));
    }
}
