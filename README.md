This module is used for create upgrade.sql and revert.sql when db is change;

execute command below
```yaml
java -jar diff-1.0-SNAPSHOT-jar-with-dependencies.jar origin.sql target.sql
```

then create **upgrade.sql** and **revert.sql**

only used for MySQL !!