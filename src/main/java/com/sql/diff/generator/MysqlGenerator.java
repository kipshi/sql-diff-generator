/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sql.diff.generator;

import com.google.common.base.Joiner;
import com.sql.diff.component.DataBase;
import com.sql.diff.component.Field;
import com.sql.diff.component.Index;
import com.sql.diff.component.PrimaryKey;
import com.sql.diff.component.Table;
import com.sql.diff.component.UniqueKey;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Set;

public class MysqlGenerator {

    public static String upgradeSql(DataBase origin, DataBase target) {
        String upgradeSql = "";
        String originDB = origin.getDbName();
        String targetDB = target.getDbName();
        if (StringUtils.isBlank(originDB) || !originDB.equals(targetDB)) {
            return target.getOriginSql();
        } else {
            upgradeSql += "USE " + targetDB + ";\n";
        }

        Map<String, Table> originTables = origin.getTables();
        Map<String, Table> targetTables = target.getTables();
        for (Map.Entry<String, Table> entry : originTables.entrySet()) {
            String tableName = entry.getKey();
            if (!targetTables.containsKey(tableName)) {
                upgradeSql += generateDropTable(tableName) + "\n";
            }
        }
        for (Map.Entry<String, Table> entry : targetTables.entrySet()) {
            String tableName = entry.getKey();
            Table table = entry.getValue();
            if (!originTables.containsKey(tableName)) {
                upgradeSql += table.getOriginTableSql() + "\n";
            } else {
                String alterTableSql = generateAlterTable(originTables.get(tableName), table) + "\n";
                if (StringUtils.isNotBlank(alterTableSql)) {
                    upgradeSql += alterTableSql + "\n";
                }
            }
        }

        Set<String> originExtraSqls = origin.getExtraSqls();
        Set<String> targetExtraSqls = target.getExtraSqls();
        upgradeSql += generateExtSqls(originExtraSqls, targetExtraSqls);
        return upgradeSql;
    }

    private static String generateAlterTable(Table origin, Table target) {
        String upgradeSql = "ALTER TABLE `" + target.getTableName() + "`\n";
        String prefix = upgradeSql;
        Map<String, Field> originFields = origin.getFields();
        Map<String, Field> targetFields = target.getFields();
        for (Map.Entry<String, Field> entry : originFields.entrySet()) {
            String fieldName = entry.getKey();
            if (!targetFields.containsKey(fieldName)) {
                upgradeSql += generateDropField(fieldName) + "\n";
            }
        }
        for (Map.Entry<String, Field> entry : targetFields.entrySet()) {
            String fieldName = entry.getKey();
            Field field = entry.getValue();
            if (!originFields.containsKey(fieldName)) {
                upgradeSql += generateAddField(field) + "\n";
            } else {
                Field originField = originFields.get(fieldName);
                if (!originField.equals(field)) {
                    upgradeSql += generateModifyField(field) + "\n";
                }
            }
        }

        PrimaryKey originPrimaryKey = origin.getPrimaryKey();
        PrimaryKey targetPrimaryKey = target.getPrimaryKey();
        if (originPrimaryKey == null && targetPrimaryKey != null) {
            upgradeSql += generateAddPrimaryKey(targetPrimaryKey) + "\n";
        } else if (originPrimaryKey != null && targetPrimaryKey == null) {
            upgradeSql += generateDropPrimaryKey() + "\n";
        } else if (originPrimaryKey != null && targetPrimaryKey != null) {
            if (!originPrimaryKey.equals(targetPrimaryKey)) {
                upgradeSql += generateDropPrimaryKey() + "\n";
                upgradeSql += generateAddPrimaryKey(targetPrimaryKey) + "\n";
            }
        }

        Map<String, UniqueKey> originUniqueKeys = origin.getUniqueKeys();
        Map<String, UniqueKey> targetUniqueKeys = target.getUniqueKeys();
        for (Map.Entry<String, UniqueKey> entry : originUniqueKeys.entrySet()) {
            String keyName = entry.getKey();
            if (!targetUniqueKeys.containsKey(keyName)) {
                upgradeSql += generateDropIndex(entry.getValue().getKeyName()) + "\n";
            }
        }
        for (Map.Entry<String, UniqueKey> entry : targetUniqueKeys.entrySet()) {
            String keyName = entry.getKey();
            UniqueKey uniqueKey = entry.getValue();
            if (!originUniqueKeys.containsKey(keyName)) {
                upgradeSql += generateUniqKey(uniqueKey) + "\n";
            } else {
                UniqueKey originUniqKey = originUniqueKeys.get(keyName);
                if (!originUniqKey.equals(uniqueKey)) {
                    upgradeSql += generateDropIndex(originUniqKey.getKeyName()) + "\n";
                    upgradeSql += generateUniqKey(uniqueKey) + "\n";
                }
            }
        }

        Map<String, Index> originIndexs = origin.getIndexs();
        Map<String, Index> targetIndexs = target.getIndexs();
        for (Map.Entry<String, Index> entry : originIndexs.entrySet()) {
            String keyName = entry.getKey();
            if (!targetIndexs.containsKey(keyName)) {
                upgradeSql += generateDropIndex(entry.getValue().getKeyName()) + "\n";
            }
        }
        for (Map.Entry<String, Index> entry : targetIndexs.entrySet()) {
            String keyName = entry.getKey();
            Index index = entry.getValue();
            if (!originIndexs.containsKey(keyName)) {
                upgradeSql += generateIndex(index) + "\n";
            } else {
                Index originIndex = originIndexs.get(keyName);
                if (!originIndex.equals(index)) {
                    upgradeSql += generateDropIndex(originIndex.getKeyName()) + "\n";
                    upgradeSql += generateIndex(index) + "\n";
                }
            }
        }

        if (prefix.equals(upgradeSql)) {
            return "";
        }
        if (upgradeSql.endsWith(",")) {
            upgradeSql = upgradeSql.substring(0, upgradeSql.length() - 1);
        } else if (upgradeSql.endsWith(",\n")) {
            upgradeSql = upgradeSql.substring(0, upgradeSql.length() - 2);
        }
        return upgradeSql + ";";
    }

    private static String generateIndex(Index index) {
        String combineFields = Joiner.on(",").join(index.getCombinedFields());
        return String.format("ADD INDEX %s (%s),", index.getKeyName(), combineFields);
    }

    private static String generateUniqKey(UniqueKey uniqueKey) {
        String combineFields = Joiner.on(",").join(uniqueKey.getCombinedFields());
        return String.format("ADD CONSTRAINT %s UNIQUE (%s),", uniqueKey.getKeyName(), combineFields);
    }

    private static String generateDropIndex(String key) {
        return String.format("DROP INDEX %s,", key);
    }

    private static String generateAddField(Field field) {
        return String.format("ADD COLUMN  `%s` %s %s", field.getFieldName(), field.getFieldType(), field.getExtra());
    }

    private static String generateModifyField(Field field) {
        return String.format("MODIFY COLUMN  `%s` %s %s", field.getFieldName(), field.getFieldType(),
                field.getExtra());
    }

    private static String generateDropField(String fieldName) {
        return String.format("DROP COLUMN  `%s`,", fieldName);
    }

    private static String generateAddPrimaryKey(PrimaryKey primaryKey) {
        return String.format("ADD PRIMARY KEY  `(%s)`,", primaryKey.getField());
    }

    private static String generateDropPrimaryKey() {
        return "DROP PRIMARY KEY,";
    }

    private static String generateExtSqls(Set<String> originSqls, Set<String> targetSqls) {
        String extSql = "";
        for (String sql : targetSqls) {
            if (!originSqls.contains(sql)) {
                String truncateSql = generateTruncateSql(sql);
                if (truncateSql != null) {
                    extSql += truncateSql + "\n";
                }
                extSql += sql + "\n";
            }
        }
        return extSql;
    }

    private static String generateTruncateSql(String sql) {
        if (!sql.startsWith("INSERT INTO")) {
            return null;
        }
        sql = sql.replace("INSERT INTO", "");
        int endIndex = sql.indexOf("(");
        String tableName = sql.substring(0, endIndex).replace("`", "").trim();
        return String.format("TRUNCATE TABLE %s;", tableName);
    }

    private static String generateDropTable(String tableName) {
        return "DROP TABLE IF EXISTS `" + tableName + "`;";
    }

}
