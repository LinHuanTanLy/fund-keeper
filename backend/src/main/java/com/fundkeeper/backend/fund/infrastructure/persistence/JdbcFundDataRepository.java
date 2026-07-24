package com.fundkeeper.backend.fund.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.fund.domain.FeeCalculationMethod;
import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.OfficialNav;
import com.fundkeeper.backend.fund.domain.PurchaseFeeRule;

@Repository
public class JdbcFundDataRepository implements FundDataRepository {

    private static final String FUND_COLUMNS = """
            SELECT id, code, name, category, currency, supported,
                   confirmation_delay_trading_days, data_source,
                   created_at, updated_at
              FROM funds
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcFundDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<FundDefinition> findFundByCode(String code) {
        return first(jdbcTemplate.query(
                FUND_COLUMNS + " WHERE code = ?",
                this::mapFund,
                code));
    }

    @Override
    public Optional<FundDefinition> findFundById(long id) {
        return first(jdbcTemplate.query(
                FUND_COLUMNS + " WHERE id = ?",
                this::mapFund,
                id));
    }

    @Override
    public Optional<Boolean> findTradingDayOpenFlag(LocalDate date) {
        return first(jdbcTemplate.query(
                """
                SELECT is_open
                  FROM fund_trading_days
                 WHERE market = 'CN_FUND'
                   AND trade_date = ?
                """,
                (resultSet, rowNumber) ->
                        resultSet.getBoolean("is_open"),
                date));
    }

    @Override
    public Optional<OfficialNav> findOfficialNav(
            long fundId,
            LocalDate navDate) {
        return first(jdbcTemplate.query(
                """
                SELECT nav_date, unit_nav, data_source
                  FROM fund_navs
                 WHERE fund_id = ?
                   AND nav_date = ?
                """,
                (resultSet, rowNumber) -> new OfficialNav(
                        resultSet.getObject("nav_date", LocalDate.class),
                        resultSet.getBigDecimal("unit_nav"),
                        resultSet.getString("data_source")),
                fundId,
                navDate));
    }

    @Override
    public Optional<OfficialNav> findLatestOfficialNav(long fundId) {
        return first(jdbcTemplate.query(
                """
                SELECT nav_date, unit_nav, data_source
                  FROM fund_navs
                 WHERE fund_id = ?
                 ORDER BY nav_date DESC
                 LIMIT 1
                """,
                (resultSet, rowNumber) -> new OfficialNav(
                        resultSet.getObject("nav_date", LocalDate.class),
                        resultSet.getBigDecimal("unit_nav"),
                        resultSet.getString("data_source")),
                fundId));
    }

    @Override
    public Optional<PurchaseFeeRule> findPurchaseFeeRule(
            long fundId,
            BigDecimal amount,
            LocalDate effectiveDate) {
        return first(jdbcTemplate.query(
                """
                SELECT fee_rate, calculation_method, data_source
                  FROM fund_purchase_fee_rules
                 WHERE fund_id = ?
                   AND minimum_amount <= ?
                   AND (maximum_amount IS NULL OR maximum_amount > ?)
                   AND effective_from <= ?
                   AND (effective_to IS NULL OR effective_to >= ?)
                 ORDER BY minimum_amount DESC, effective_from DESC
                 LIMIT 1
                """,
                (resultSet, rowNumber) -> new PurchaseFeeRule(
                        resultSet.getBigDecimal("fee_rate"),
                        FeeCalculationMethod.valueOf(
                                resultSet.getString("calculation_method")),
                        resultSet.getString("data_source")),
                fundId,
                amount,
                amount,
                effectiveDate,
                effectiveDate));
    }

    private FundDefinition mapFund(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new FundDefinition(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                FundCategory.valueOf(resultSet.getString("category")),
                resultSet.getString("currency"),
                resultSet.getBoolean("supported"),
                (Integer) resultSet.getObject(
                        "confirmation_delay_trading_days"),
                resultSet.getString("data_source"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
