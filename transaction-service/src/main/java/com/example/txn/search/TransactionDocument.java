package com.example.txn.search;

import com.example.txn.dto.RuleResult;
import com.example.txn.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * A scored transaction as stored in Elasticsearch. Identifiers are keywords (exact match and
 * filtering), {@code explanations} is analyzed text, and {@code firedRules} lists the names of
 * the rules that fired so searches can filter on them.
 */
@Document(indexName = TransactionDocument.INDEX, createIndex = false)
public class TransactionDocument {

    public static final String INDEX = "transactions";

    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String accountId;
    @Field(type = FieldType.Double)
    private double amount;
    @Field(type = FieldType.Keyword)
    private String currency;
    @Field(type = FieldType.Keyword)
    private String merchantId;
    @Field(type = FieldType.Keyword)
    private String merchantCategory;
    @Field(type = FieldType.Keyword)
    private String country;
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant eventTime;
    @Field(type = FieldType.Keyword)
    private String status;
    @Field(type = FieldType.Integer)
    private Integer riskScore;
    @Field(type = FieldType.Keyword)
    private List<String> firedRules;
    @Field(type = FieldType.Text)
    private String explanations;

    public TransactionDocument() {
    }

    public static TransactionDocument from(TransactionResponse r) {
        TransactionDocument d = new TransactionDocument();
        d.id = r.id();
        d.accountId = r.accountId();
        d.amount = r.amount().doubleValue();
        d.currency = r.currency();
        d.merchantId = r.merchantId();
        d.merchantCategory = r.merchantCategory();
        d.country = r.country();
        d.eventTime = r.eventTime();
        d.status = r.status().name();
        d.riskScore = r.riskScore();
        List<RuleResult> rules = r.ruleResults() == null ? List.of() : r.ruleResults();
        d.firedRules = rules.stream().filter(RuleResult::fired).map(RuleResult::rule).toList();
        d.explanations = rules.stream().filter(RuleResult::fired).map(RuleResult::explanation)
                .collect(Collectors.joining(". "));
        return d;
    }

    public TransactionSearchHit toHit() {
        return new TransactionSearchHit(id, accountId, BigDecimal.valueOf(amount), currency, merchantId,
                merchantCategory, country, eventTime, status, riskScore,
                firedRules == null ? List.of() : firedRules);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public List<String> getFiredRules() {
        return firedRules;
    }

    public void setFiredRules(List<String> firedRules) {
        this.firedRules = firedRules;
    }

    public String getExplanations() {
        return explanations;
    }

    public void setExplanations(String explanations) {
        this.explanations = explanations;
    }
}
