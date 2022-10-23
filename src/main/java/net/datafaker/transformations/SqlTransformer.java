package net.datafaker.transformations;

import java.util.List;

public class SqlTransformer<IN> implements Transformer<IN, CharSequence> {
    private static final String INSERT_INTO_UP = "INSERT INTO ";
    private static final String INSERT_INTO_LW = "insert into ";
    private static final String VALUES_UP = "VALUES ";
    private static final String VALUES_LW = "values ";
    private static final char DEFAULT_QUOTE = '\'';
    private static final char DEFAULT_CATALOG_SEPARATOR = '.';
    private static final String DEFAULT_SQL_IDENTIFIER = "\"\"";

    private final Casing casing;
    private final char quote;
    private final char openSqlIdentifier;
    private final char closeSqlIdentifier;
    private final String tableName;
    private final String schemaName;

    private final boolean withBatchMode;
    private final boolean keywordUpperCase;

    private SqlTransformer(String schemaName, String tableName, char quote, String sqlIdentifier, Casing casing, boolean withBatchMode, boolean keywordUpperCase) {
        this.schemaName = schemaName;
        this.quote = quote;
        this.openSqlIdentifier = sqlIdentifier.charAt(0);
        this.closeSqlIdentifier = sqlIdentifier.length() == 1 ? sqlIdentifier.charAt(0) : sqlIdentifier.charAt(1);
        this.tableName = tableName;
        this.casing = casing;
        this.withBatchMode = withBatchMode;
        this.keywordUpperCase = keywordUpperCase;
    }

    private boolean isSqlQuoteIdentifierRequiredFor(String name) {
        for (int i = 0; i < name.length(); i++) {
            if (casing == Casing.TO_UPPER && Character.isLowerCase(name.charAt(i))
                || casing == Casing.TO_LOWER && Character.isUpperCase(name.charAt(i))
                || name.charAt(i) == openSqlIdentifier
                || name.charAt(i) == closeSqlIdentifier) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CharSequence apply(IN input, Schema<IN, ?> schema, int rowId) {
        //noinspection unchecked
        Field<?, ? extends CharSequence>[] fields = (Field<?, ? extends CharSequence>[]) schema.getFields();
        if (fields.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!withBatchMode || rowId == 0) {
            sb.append(keywordUpperCase ? INSERT_INTO_UP : INSERT_INTO_LW);
            appendNameToQuery(sb, schemaName);
            if (schemaName != null && !schemaName.isEmpty()) {
                sb.append(DEFAULT_CATALOG_SEPARATOR);
            }
            appendNameToQuery(sb, tableName);
            sb.append(" (");
            for (int i = 0; i < fields.length; i++) {
                final String fieldName = fields[i].getName();
                final boolean sqlIdentifierRequired = isSqlQuoteIdentifierRequiredFor(fieldName);
                if (sqlIdentifierRequired) {
                    sb.append(openSqlIdentifier);
                }
                for (int j = 0; j < fieldName.length(); j++) {
                    if (openSqlIdentifier == fieldName.charAt(j)
                      || closeSqlIdentifier == fieldName.charAt(j)) {
                        sb.append(openSqlIdentifier);
                    }
                    sb.append(fieldName.charAt(j));
                }
                if (sqlIdentifierRequired) {
                    sb.append(closeSqlIdentifier);
                }
                if (i < fields.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")");
            sb.append(withBatchMode ? "\n": " ");
            sb.append(keywordUpperCase ? VALUES_UP : VALUES_LW).append("(");
        } else {
            sb.append(",\n").append("       ("); // "VALUES ".length() number of spaces indentation
        }
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] instanceof SimpleField) {
                //noinspection unchecked
                Object value = ((SimpleField<Object, ? extends CharSequence>) fields[i]).transform(input);
                Class<?> clazz = value == null ? null : value.getClass();
                if (value == null
                    || value instanceof java.lang.Number
                    || value instanceof java.lang.Boolean
                    || clazz.isPrimitive()) {
                    sb.append(value);
                } else {
                    String strValue = value.toString();
                    sb.append(quote);
                    for (int j = 0; j < strValue.length(); j++) {
                        if (strValue.charAt(j) == quote) {
                            sb.append(quote);
                        }
                        sb.append(strValue.charAt(j));
                    }
                    sb.append(quote);
                }
            }
            if (i < fields.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    private void appendNameToQuery(StringBuilder sb, String name) {
        if (name == null || name.isEmpty()) return;
        boolean sqlIdentifierRequired = isSqlQuoteIdentifierRequiredFor(name);

        if (sqlIdentifierRequired) {
            sb.append(openSqlIdentifier);
        }
        sb.append(name);
        if (sqlIdentifierRequired) {
            sb.append(closeSqlIdentifier);
        }
    }

    @Override
    public String generate(List<IN> input, Schema<IN, ?> schema) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.size(); i++) {
            sb.append(apply(input.get(i), schema, i));
            if (i == input.size() - 1 && sb.length() > 0) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    @Override
    public String generate(Schema<IN, ?> schema, int limit) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(apply(null, schema, i));
            if (i == limit - 1 && sb.length() > 0) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    public static class SqlTransformerBuilder<IN> {
        private char quote = DEFAULT_QUOTE;
        private String sqlQuoteIdentifier = DEFAULT_SQL_IDENTIFIER;
        private String tableName = "MyTable";
        private String schemaName = "";
        private Casing casing = Casing.TO_UPPER;
        private boolean withBatchMode = false;
        private boolean keywordUpperCase = true;

        private SqlDialect dialect;

        public SqlTransformerBuilder<IN> dialect(SqlDialect dialect) {
            sqlQuoteIdentifier = dialect.getSqlQuoteIdentifier();
            casing = dialect.getUnquotedCasing();
            this.dialect = dialect;
            return this;
        }

        public SqlTransformerBuilder<IN> casing(Casing casing) {
            this.casing = casing;
            return this;
        }

        public SqlTransformerBuilder<IN> quote(char quote) {
            this.quote = quote;
            return this;
        }

        public SqlTransformerBuilder<IN> sqlQuoteIdentifier(String sqlQuoteIdentifier) {
            this.sqlQuoteIdentifier = sqlQuoteIdentifier;
            return this;
        }

        public SqlTransformerBuilder<IN> tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public SqlTransformerBuilder<IN> schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public SqlTransformerBuilder<IN> batch(boolean withBatchMode) {
            this.withBatchMode = withBatchMode;
            return this;
        }

        public SqlTransformerBuilder<IN> keywordUpperCase(boolean keywordUpperCase) {
            this.keywordUpperCase = keywordUpperCase;
            return this;
        }

        public SqlTransformer<IN> build() {
            return new SqlTransformer<>(
                schemaName, tableName, quote, sqlQuoteIdentifier, casing,
                withBatchMode && dialect.isSupportBulkInsert(), keywordUpperCase);
        }
    }
}
