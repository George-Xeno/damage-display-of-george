# libs/

第三方模组依赖 jar 放这里。

本模组**当前不需要任何第三方依赖**（只依赖 NeoForge 和原版 Minecraft），
所以这个目录正常是空的。

`build.gradle` 里的这两行是给将来用的：

```gradle
compileOnly fileTree(dir: 'libs', include: '*.jar')
runtimeOnly fileTree(dir: 'libs', include: '*.jar')
```

- `compileOnly`：只用来编译，运行时由整合包提供
- `runtimeOnly`：让 `gradlew runClient/runServer` 的开发环境里也带上它们

**不要把 libs/ 里的 jar 提交进 git**（已在 `.gitignore` 里排除）。
