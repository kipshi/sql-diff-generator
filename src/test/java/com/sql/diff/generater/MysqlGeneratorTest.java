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

package com.sql.diff.generater;

import com.sql.diff.SqlFile;
import com.sql.diff.component.DataBase;
import com.sql.diff.generator.MysqlGenerator;
import org.junit.Test;

public class MysqlGeneratorTest {

    @Test
    public void testUpgradeSql() {
        String rootPath = this.getClass().getClassLoader().getResource("").getPath();
        String originFile = rootPath + "origin.sql";
        SqlFile originSqlFile = new SqlFile(originFile);
        DataBase originDB = originSqlFile.parse();
        String targetFile = rootPath + "target.sql";
        SqlFile targetSqlFile = new SqlFile(targetFile);
        DataBase targetDB = targetSqlFile.parse();
        String sql = MysqlGenerator.upgradeSql(originDB, targetDB);
        System.out.println(sql);
    }

}
