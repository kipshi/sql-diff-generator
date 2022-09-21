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

package com.sql.diff.parser;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sql.diff.component.DataBase;
import com.sql.diff.component.Field;
import com.sql.diff.component.Index;
import com.sql.diff.component.PrimaryKey;
import com.sql.diff.component.Table;
import com.sql.diff.component.UniqueKey;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MysqlParser {

    public static DataBase parse(BufferedReader reader) throws IOException {
        String sql = reader.readLine();
        String database = null;
        Map<String, Table> tables = Maps.newHashMap();
        Set<String> extraSqls = Sets.newHashSet();
        String originSql = "";
        String fragmentSql = "";
        while (sql != null) {
            sql = sql.trim();
            if (StringUtils.isBlank(sql)) {
                sql = reader.readLine();
                continue;
            }
            if (sql.startsWith("--")
                    || sql.startsWith("/*")
                    || sql.startsWith("*")
            ) {
                sql = reader.readLine();
                continue;
            }
            if (!sql.endsWith(";")) {
                fragmentSql += sql + "\n";
                originSql += sql + "\n";
                sql = reader.readLine();
                continue;
            }
            fragmentSql += sql + "\n";
            if (fragmentSql.startsWith("CREATE DATABASE IF NOT EXISTS")
                    || fragmentSql.startsWith("CREATE DATABASE")
                    || fragmentSql.startsWith("USE")) {
                database = parseDbName(fragmentSql);
            }
            if (fragmentSql.startsWith("CREATE TABLE IF NOT EXISTS")
                    || fragmentSql.startsWith("CREATE TABLE")) {
                Table table = parseTable(fragmentSql);
                tables.put(table.getTableName(), table);
            }
            if (fragmentSql.startsWith("INSERT INTO")) {
                extraSqls.add(fragmentSql);
            }
            fragmentSql = "";
            originSql += sql + "\n";
            sql = reader.readLine();
        }
        return new DataBase(originSql, database, tables, extraSqls);
    }

    private static String parseDbName(String sql) {
        return sql.replace("CREATE DATABASE IF NOT EXISTS", "")
                .replace("CREATE DATABASE", "")
                .replace("USE", "")
                .replace("`", "")
                .replace(";", "")
                .trim();
    }

    private static Table parseTable(String sql) {
        String originSql = sql;
        String tableName = "";
        Map<String, Field> fields = Maps.newHashMap();
        final PrimaryKey[] primaryKey = {null};
        Map<String, UniqueKey> uniqueKeys = Maps.newHashMap();
        Map<String, Index> indexs = Maps.newHashMap();
        int startIndex = sql.indexOf("(");
        int endIndex = sql.lastIndexOf(")");
        tableName = parseTableName(sql.substring(0, startIndex));
        Arrays.stream(sql.substring(startIndex + 1, endIndex).split("\n"))
                .forEach(
                        subSql -> {
                            if (subSql.startsWith("KEY")) {
                                Index idx = parseIndex(subSql);
                                indexs.put(idx.getKeyName(), idx);
                            }
                            if (subSql.startsWith("UNIQUE KEY")) {
                                UniqueKey uniqueKey = parseUniqKey(subSql);
                                uniqueKeys.put(uniqueKey.getKeyName(), uniqueKey);
                            }
                            if (subSql.startsWith("PRIMARY KEY")) {
                                primaryKey[0] = parsePrimaryKey(subSql);
                            }
                            if (subSql.contains("COMMENT")) {
                                Field field = parseField(subSql);
                                fields.put(field.getFieldName(), field);
                            }
                        }
                );

        return new Table(originSql, tableName, fields, primaryKey[0], uniqueKeys, indexs);
    }

    private static Index parseIndex(String sql) {
        String originSql = sql;
        sql = sql.replace("KEY", "").trim();
        int startIndex = sql.indexOf("(");
        int endIndex = sql.indexOf(")");
        List<String> fields = Stream.of(sql.substring(startIndex + 1, endIndex)
                .split(",")).map(field -> field.replace("`", "")).collect(Collectors.toList());
        String indexName = sql.substring(0, startIndex).replace("`", "").trim();
        return new Index(originSql, indexName, fields);
    }

    private static UniqueKey parseUniqKey(String sql) {
        String originSql = sql;
        sql = sql.replace("UNIQUE KEY", "").trim();
        int startIndex = sql.indexOf("(");
        int endIndex = sql.indexOf(")");
        List<String> fields = Stream.of(sql.substring(startIndex + 1, endIndex)
                .split(",")).map(field -> field.replace("`", "")).collect(Collectors.toList());
        String keyName = sql.substring(0, startIndex).replace("`", "").trim();
        return new UniqueKey(originSql, keyName, fields);
    }

    private static PrimaryKey parsePrimaryKey(String sql) {
        String originSql = sql;
        sql = sql.replace("PRIMARY KEY", "").trim();
        int startIndex = sql.indexOf("(");
        int endIndex = sql.indexOf(")");
        List<String> fields = Stream.of(sql.substring(startIndex + 1, endIndex)
                .split(",")).map(field -> field.replace("`", "")).collect(Collectors.toList());
        String keyName = sql.substring(0, startIndex).trim();
        return new PrimaryKey(originSql, keyName, fields.get(0));
    }

    private static Field parseField(String sql) {
        String originSql = sql;
        int startIndex = sql.indexOf("`");
        int endIndex = sql.indexOf("`", startIndex + 1);
        String fieldName = sql.substring(startIndex + 1, endIndex);
        sql = sql.substring(endIndex + 1).trim();
        endIndex = sql.indexOf(" ");
        String fieldType = sql.substring(0, endIndex).trim();
        String extraSql = sql.substring(endIndex).trim();
        return new Field(originSql, fieldName, fieldType, extraSql);
    }

    private static String parseTableName(String sql) {
        return sql.replace("CREATE TABLE IF NOT EXISTS", "")
                .replace("CREATE TABLE", "")
                .replace(";", "")
                .replace("`", "")
                .trim();
    }

}
