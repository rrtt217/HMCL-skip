# HMCL-skip

用 Java agent 在运行期把 HMCL 的「中国大陆」判定改成 `false`。**不修改 jar**，所以 HMCL 的自签名校验、
微软登录、自更新都不受影响。目标版本：HMCL 3.17.0.362（3.14.0.342 里还没有 `RegionUtils`）。

> 许可证：GPL-3.0-or-later（见 [LICENSE](LICENSE)）。本仓库的代码、脚本与文档**完全由 AI 生成**，详见文末。

```bash
java -javaagent:dist/region-agent.jar -jar HMCL-3.17.0.362.jar
```

启动日志里会多一行 `[region-agent] transformed org/jackhuang/hmcl/util/RegionUtils`。

## 为什么是 agent，而不是改 jar

`IntegrityChecker` 会对 jar 里**每个 entry** 算 SHA-512，再用 `assets/hmcl_signature_publickey.der`
验证 `META-INF/hmcl_signature`。改一个字节 → `isSelfVerified()=false` → `isOfficial()=false`，而：

- `ui/account/MicrosoftAccountLoginPane.java:144`：非官方版本拒绝微软登录
- `ui/account/CreateAccountPane.java:354`：非官方版本只允许离线账户

`-Dhmcl.self_integrity_check.disable=true` 只对自更新生效，这两处不认。所以“改 jar”这条路会让微软账户不可用；
agent 不改字节，验签照常通过，代价只是要在启动命令上加一个 `-javaagent`。

## 用法

```bash
# 1) 只改网络判定（默认）：只影响下载页的两个“国内网络”提示条
java -javaagent:dist/region-agent.jar -jar HMCL-3.17.0.362.jar

# 2) 连 machine 判定一起改（下载源、联机、离线账户默认值都会按“海外”走）
java -javaagent:dist/region-agent.jar=machine -jar HMCL.jar

# 3) 只改某个类的调用点，其它逻辑仍认为在中国大陆
java -javaagent:"dist/region-agent.jar=callsites=^org/jackhuang/hmcl/download/HMCLDownloadProvider$;machine" -jar HMCL.jar
```

### 参数

`-javaagent:region-agent.jar=<参数>`，多个参数用 `;` 分隔：

| 参数 | 作用 |
|---|---|
| （空） | 方法级：`isIsChinaMainlandNetwork()` 直接返回 `false` |
| `machine` | 同时把 `isIsChinaMainlandMachine()` 改成 `false` |
| `callsites` | 改为调用点级：只把 `invokestatic` 换成 `iconst_0`，`RegionUtils` 本体不动 |
| `callsites=<regex>` | 同上，但只处理类名（internal name）匹配的类，可重复给多个 |
| `dump=<dir>` | 把改写后的 class 落盘，方便 `javap` 核对 |

例：`-javaagent:region-agent.jar=callsites=^org/jackhuang/hmcl/download/;machine;dump=/tmp/dump`

`.sh` 和 `.exe` 发行版都不用改文件——两个启动器都读 `HMCL_JAVA_OPTS`，见下一节。

## 启动方式：.jar / .sh / .exe

### `.jar`

```bash
java -javaagent:/path/to/region-agent.jar -jar HMCL-3.17.0.362.jar
```

### `.sh`（Linux / macOS）

`HMCL-3.17.0.362.sh` 是「bash 头部 + zip 尾部」的自解压文件，脚本里会 `exec java $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"`，
而 `_HMCL_VM_OPTIONS` 优先取环境变量 `HMCL_JAVA_OPTS`：

```bash
HMCL_JAVA_OPTS="-javaagent:$PWD/dist/region-agent.jar" ./HMCL-3.17.0.362.sh
```

想固化进文件（以后直接双击/直接执行），用仓库里的 `inject-sh.sh` 改写头部那一行：

```bash
./inject-sh.sh HMCL-3.17.0.362.sh dist/region-agent.jar
# → HMCL-3.17.0.362-agent.sh，头部变成 _HMCL_VM_OPTIONS="-javaagent:<绝对路径> -XX:..."
```

改的是 zip 之前的前缀，zip 字节不动；脚本会用 `unzip -t` 复核（这种 polyglot 文件本来就有
`extra bytes at beginning` 警告，只看 `No errors detected`）。

### `.exe`（Windows）

`HMCL-3.17.0.362.exe` 是 `HMCLauncher`（3.7.0.1）PE 壳（454 144 字节）+ 与 release 完全相同的 jar
（实测尾部 11 660 275 字节的 sha256 与 `HMCL-3.17.0.362.jar` 一致），壳里明确支持：

- `HMCL_JAVA_HOME`：指定 JRE；
- `HMCL_JAVA_OPTS`：JVM 参数，最终执行 `java <HMCL_JAVA_OPTS> -jar "<解出来的 jar>"`；
- `HMCL_LAUNCHER_VERBOSE_OUTPUT`：打印启动器日志（排查时有用）。

所以只要在启动前设置环境变量，**不用改 exe**：

```bat
set HMCL_JAVA_OPTS=-javaagent:C:\HMCL\region-agent.jar
HMCL-3.17.0.362.exe
```

或者用 `examples/run-hmcl.bat`（放在 exe 同目录，双击即可）：

```bat
@echo off
set "HMCL_JAVA_OPTS=-javaagent:%~dp0region-agent.jar"
start "" "%~dp0HMCL-3.17.0.362.exe"
```

两个坑：

- `HMCL_JAVA_OPTS` 是**替换**默认值，不是追加。`.sh` 的默认是
  `-XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15 -XX:G1PeriodicGCInterval=...`，
  `.exe` 的默认是 ` -Xmx1G -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15`；想保留就一并写进去。
- 启动器按空格切分参数，**agent 路径别带空格**（`C:\Program Files\...` 会断）。

### 兜底：`JAVA_TOOL_OPTIONS`

任何 JVM 都会读，适合启动器不认参数的情况（会多一行 `Picked up JAVA_TOOL_OPTIONS: ...`）：

```bash
JAVA_TOOL_OPTIONS="-javaagent:/path/to/region-agent.jar" ./HMCL-3.17.0.362.sh
```

```bat
set JAVA_TOOL_OPTIONS=-javaagent:C:\HMCL\region-agent.jar
HMCL-3.17.0.362.exe
```

### 升级注意

`.sh` / `.exe` 自更新时会被新文件整体替换，写进头部的注入会丢；`region-agent.jar` 和包装脚本不受影响，
重跑一次 `inject-sh.sh`（或继续用环境变量）即可。

也不建议去替换 `.exe` 里追加的那份 jar：换了就破坏 HMCL 的自签名（`isOfficial()` 变 false，微软登录失效），
而这正是 agent 方案要避免的。

## 判定点与调用点（3.17.0.362 实测，共 9 处）

下表是 JVM internal name，可直接粘进 `callsites=`；内部类的 `$` 在正则里要写成 `\$`。
上游源码在 `HMCL/src/main/java/<包路径>/<外层类>.java`，内部类取 `$` 之前的部分（例如 `.../ui/download/VersionsPage$VersionsPageSkin` → `HMCL/src/main/java/org/jackhuang/hmcl/ui/download/VersionsPage.java`）。

| 类路径 | 方法 | 判定 | 影响 |
|---|---|---|---|
| `org/jackhuang/hmcl/download/HMCLDownloadProvider` | `getCandidates(DownloadSource, WebURL, WebURL)` | machine | 国内时官方 + 镜像两个候选都给 |
| `org/jackhuang/hmcl/download/HMCLDownloadProvider` | `getCandidates(DownloadSource, List, List)` | machine | 同上（列表版） |
| `org/jackhuang/hmcl/download/HMCLDownloadProvider` | `getCleanroomVersionListCandidates()` | machine | Cleanroom 版本列表的镜像候选 |
| `org/jackhuang/hmcl/terracotta/TerracottaMetadata$Config` | `resolve(Options)` | machine | 联机下载源顺序：国内把 `downloadsCN` 排前面 |
| `org/jackhuang/hmcl/terracotta/TerracottaNodeList` | `lambda$fetch$0(TerracottaNode)` | machine | 只保留与本地区一致的节点 |
| `org/jackhuang/hmcl/ui/terracotta/TerracottaControllerPage` | `<init>()` | machine | **非**中国大陆时显示 `terracotta.unsupported.region` 警告条 |
| `org/jackhuang/hmcl/ui/account/AccountListPage` | `<clinit>()` | machine | 国内默认放行离线账户；置 false 后回落到设置项（默认隐藏） |
| `org/jackhuang/hmcl/ui/download/VersionsPage$VersionsPageSkin` | `<init>(VersionsPage)` | **network** | 下载页顶部 `download.china` 提示条 |
| `org/jackhuang/hmcl/ui/instances/DownloadListPage$ModDownloadListPageSkin` | `<init>(DownloadListPage)` | **network** | 模组下载页同样的提示条 |

按类精确打补丁（外层用单引号，避免 shell 展开 `$`）：

```bash
# 只改下载源相关的 3 处（其余逻辑仍按“国内”走）
java '-javaagent:dist/region-agent.jar=callsites=^org/jackhuang/hmcl/download/HMCLDownloadProvider$;machine' -jar HMCL.jar

# 只去掉两个“国内网络”提示条（这两处只调 network，不需要 machine）
java '-javaagent:dist/region-agent.jar=callsites=^org/jackhuang/hmcl/ui/(download/VersionsPage\$VersionsPageSkin|instances/DownloadListPage\$ModDownloadListPageSkin)$' -jar HMCL.jar

# 只改联机（Terracotta）的 3 处
java '-javaagent:dist/region-agent.jar=callsites=^org/jackhuang/hmcl/(terracotta/|ui/terracotta/);machine' -jar HMCL.jar
```

两个判定的区别：

- `isIsChinaMainlandMachine()`：纯本地判断（时区 `Asia/Shanghai`、UTC+8 + `CN` locale、Windows `GetUserGeoID`），
  在 `<clinit>` 里算一次，不发网络请求。下载源、联机、离线账户看它。
- `isIsChinaMainlandNetwork()`：先看 machine，再请求 `https://ipwho.is`；**失败时返回 `true`**（fail-closed），
  所以屏蔽域名/断网只会让它更像国内。只有两个 UI 提示条看它。

## 原理

`premain` 里注册一个 `ClassFileTransformer`，用 ASM 树 API 改写：

- **方法级**：清空 `instructions` / `tryCatchBlocks` / 局部变量表，写入 `iconst_0; ireturn`，`maxStack=1, maxLocals=0`。
- **调用点级**：遍历指令，把 `invokestatic RegionUtils.xxx()Z` 换成 `iconst_0`。两者栈效果一致（弹 0 个、压 1 个 int），
  所以栈帧不用动，`ClassWriter(COMPUTE_MAXS)` 重算偏移和 `max_stack` 即可。等价的手工改法是
  `B8 <u2>` → `03 00 00`（`iconst_0; nop; nop`），长度不变。
  调用点级的好处是 `RegionUtils` 本体仍是原样，其它调用方（或反射）看到的行为不变。

agent jar 里内嵌了 ASM（HMCL 自身不带 ASM，不会冲突），所以运行时不需要额外的 `-cp`。

## 验证

```bash
# 行为探针：打印两个判定的返回值
java -javaagent:dist/region-agent.jar        -cp work/HMCL-3.17.0.362.jar test/RegionProbe.java
java -javaagent:dist/region-agent.jar=machine -cp work/HMCL-3.17.0.362.jar test/RegionProbe.java

# 调用点级：强制加载目标类，观察 transformed 日志并导出改写后的 class
java "-javaagent:dist/region-agent.jar=callsites=^org/jackhuang/hmcl/download/HMCLDownloadProvider$;machine;dump=work/tmp/dump" \
     -cp work/HMCL-3.17.0.362.jar test/LoadClasses.java \
     org.jackhuang.hmcl.download.HMCLDownloadProvider
javap -p -c work/tmp/dump/org/jackhuang/hmcl/download/HMCLDownloadProvider.class | grep RegionUtils   # 应为 0
```

实测（本机时区 `Asia/Shanghai`，出口 IP 在国内）：

| 启动方式 | machine | network | 耗时 |
|---|---|---|---|
| 原版 jar | `true` | `true`（真的请求了 ipwho.is） | 664 ms |
| `-javaagent` | `true` | `false`（无网络请求） | 1 ms |
| `-javaagent=machine` | `false` | `false` | 1 ms |

所有被改写的类在 `-Xverify:all` 下都能正常加载校验。

上面「按类精确打补丁」里的三条命令也实测过：第一条只改写 `HMCLDownloadProvider`；第二条只改写两个 `*Skin`；
第三条只改写 `TerracottaMetadata$Config`、`TerracottaNodeList`、`TerracottaControllerPage` 三个类，
同一批加载的 `AccountListPage` 不在正则范围内，日志里就不会出现 `transformed`。

## 构建

```bash
./build-agent.sh     # 产出 dist/region-agent.jar（javac + jar，内嵌 ASM）
```

依赖：JDK 17+、`curl`、`unzip`。首次运行会从 Maven Central 下载 ASM 9.11.1 到 `lib/` 并校验 SHA-256
（构建期依赖，仓库不存二进制）。

## 副作用

- 默认（只改 network）：仅去掉下载页的“国内网络”提示条，其它行为不变。
- `machine`：下载源候选变少（只剩海外源）、联机节点按海外过滤并在联机页显示“该地区不支持”、
  离线账户入口回落到设置项。**只想要官方下载源的话，用 `callsites=` 精确到 `HMCLDownloadProvider`。**
- 微软登录、自更新、jar 完整性都不受影响（agent 不碰磁盘上的 jar）。

## 说明

- HMCL 是 GPL-3.0；本仓库只是运行期插桩，不包含 HMCL 代码，也不修改其分发物。
- 判定逻辑随版本会变，换版本后看启动日志有没有 `transformed ...RegionUtils`；没有的话说明类名/包名变了，需要同步调整。

## 免责声明

- 本项目与 HMCL 开发者（huangyuhui 及贡献者）、Mojang Studios、Microsoft 均无关联，也未获其授权、赞助或认可；
  “HMCL”“Minecraft”等名称与商标归各自权利人所有。
- 本项目只包含独立的 Java agent 源码与构建脚本（GPL-3.0），不包含、不修改、也不重新分发 HMCL 的任何二进制或源码。
  HMCL 本身以 GPL-3.0 发布；若你分发基于它的修改版，请自行遵守 GPL-3.0（提供完整的对应源码）。
- 用途限于本机个人使用（例如调整下载镜像偏好、隐藏地区提示），不涉及破解付费内容、绕过账号或授权校验、规避访问控制。
  微软登录、正版验证等逻辑未被改动，也不在本项目范围内。
- 地区判定会影响下载源选择与界面提示。是否使用、以及由此产生的下载来源、网络与合规风险，由使用者自行判断并承担；
  使用前请确认不违反 HMCL 使用条款、Microsoft 服务条款以及你所在地区的法律法规。
- 代码按“现状”（AS IS）提供，不附带任何明示或默示担保，包括但不限于适销性、特定用途适用性与不侵权担保。
  因使用或无法使用本工具造成的任何直接或间接损失，作者不承担责任。
- 请勿将本工具用于商业分发、二次售卖，或让修改后的运行环境冒充官方版本；由此造成的一切后果由使用者自负。

## 许可

本仓库以 **GNU General Public License v3.0 or later** 发布，SPDX 标识 `GPL-3.0-or-later`，全文见 [LICENSE](LICENSE)。
你可以再分发、修改，但需保留版权与许可声明，并以相同许可证提供对应源码。

## 关于本仓库

- 代码、脚本与文档**完全由 AI 编写**（DeepSeek Harness 编码代理，2026-10）：需求由人类提出，
  构建、运行与字节码校验也由 AI 在本地执行，人类只做审阅与发布决定。
- 因此请把它当作“可直接运行的参考实现”：文中所有数字（调用点数量、`-Xverify:all` 结果、耗时）
  都来自 3.17.0.362 的实测，但不保证覆盖所有 HMCL 版本与平台。
- 发现错误欢迎开 issue / PR；如果 HMCL 改了包名或判定位置，启动日志里不会出现
  `transformed ...RegionUtils`，按日志排查即可。
