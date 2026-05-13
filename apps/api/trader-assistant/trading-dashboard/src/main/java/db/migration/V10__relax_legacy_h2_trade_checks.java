package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V10__relax_legacy_h2_trade_checks extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    if (!connection
        .getMetaData()
        .getDatabaseProductName()
        .toLowerCase(Locale.ROOT)
        .contains("h2")) {
      return;
    }

    for (String constraintName : findLegacyTradeConstraints(connection)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE trades DROP CONSTRAINT " + quoteIdentifier(constraintName));
      }
    }
  }

  private List<String> findLegacyTradeConstraints(Connection connection) throws SQLException {
    List<String> constraintNames = new ArrayList<>();
    String sql =
        """
        SELECT tc.constraint_name, cc.check_clause
          FROM information_schema.table_constraints tc
          JOIN information_schema.check_constraints cc
            ON tc.constraint_catalog = cc.constraint_catalog
           AND tc.constraint_schema = cc.constraint_schema
           AND tc.constraint_name = cc.constraint_name
         WHERE LOWER(tc.table_name) = 'trades'
           AND tc.constraint_type = 'CHECK'
        """;

    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        String constraintName = rows.getString("constraint_name");
        String checkClause = rows.getString("check_clause").toLowerCase(Locale.ROOT);
        if (isLegacySideCheck(checkClause) || isLegacyPriceCheck(checkClause)) {
          constraintNames.add(constraintName);
        }
      }
    }
    return constraintNames;
  }

  private boolean isLegacySideCheck(String checkClause) {
    return checkClause.contains("side")
        && checkClause.contains("buy")
        && checkClause.contains("sell")
        && !checkClause.contains("expire")
        && !checkClause.contains("exercise");
  }

  private boolean isLegacyPriceCheck(String checkClause) {
    return checkClause.contains("price_per_share")
        && checkClause.contains(">")
        && !checkClause.contains(">=");
  }

  private String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
