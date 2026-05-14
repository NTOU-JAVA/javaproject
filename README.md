# javaproject
協作專案

# 編譯指令

```powershell
javac -encoding UTF-8 -cp "lib/jsoup-1.22.2.jar" -d out src\app\*.java src\model\*.java src\persistence\*.java src\service\*.java src\ui\*.java
```

# 執行指令

```powershell
java -cp "out;lib/jsoup-1.22.2.jar" app.Main
```