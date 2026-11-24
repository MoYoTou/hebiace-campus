# 建院助手（HebiaceCampus）

面向**河北建筑工程学院**学生的开源校园助手，一个 App 聚合五件事：**课表、洗浴、洗衣、学习通作业、雨课堂作业**。Android 优先（iOS 共用同一套 Compose Multiplatform 代码）。

> 本项目是学习用途的开源项目，非学校官方应用，与学校及各平台无隶属关系。

## 功能

| 模块 | 对接系统 | 能力 |
|------|---------|------|
| 课表 | 正方教务（`jwx.hebiace.edu.cn`） | 学号+密码登录，一键同步周课表，按周/星期查看 |
| 作业提醒 | 学习通（超星）+ 雨课堂 | 双账号合并，四栏（未完成/待批阅/已完成/已过期），截止倒计时 |
| 洗浴 | 趣智校园 | 短信/密码登录，蓝牙扫描附近热水器，一键开阀/关阀，消费结算 |
| 洗衣 | U净 | 验证码登录，扫洗衣机码选套餐、下单、支付、启动/停止 |

## 技术栈

- **Compose Multiplatform**（Kotlin 单代码库，Android + iOS）
- Ktor Client（HTTP）、kotlinx.serialization（JSON）、kotlinx-datetime（时间）
- Android：Keystore + AES-GCM 存凭证、BluetoothLeScanner 扫设备、WebView 登录
- 架构：`commonMain` 共享全部 UI + 数据层；平台差异用 `expect/actual`（加密、存储、BLE、WebView）

## 项目结构

```
hebiace-campus/
├── settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml
└── composeApp/
    └── src/
        ├── commonMain/kotlin/com/hebiace/campus/
        │   ├── App.kt                # 入口 + 底部导航（课程/作业/生活/我的）
        │   ├── CampusRepo.kt         # 仓库：串起各客户端
        │   ├── AppState.kt           # 登录态与配置存取
        │   ├── model/Models.kt       # 数据模型
        │   ├── data/
        │   │   ├── jw/               # 正方教务（登录+RSA/AES+课表）
        │   │   ├── xxt/              # 学习通
        │   │   ├── ykt/              # 雨课堂
        │   │   ├── qzxy/             # 趣智校园（洗浴）
        │   │   ├── ujing/            # U净（洗衣）
        │   │   └── web/              # 内置 WebView 登录
        │   ├── store/                # Settings + SecureStore(expect)
        │   ├── util/                 # MD5/Base64/时间解析
        │   └── ui/                   # 4 个页面
        ├── androidMain/              # MainActivity、加密/存储/BLE/WebView 实现
        └── iosMain/                  # iOS 实现（部分待完善，见下）
```

## 构建（Android）

1. 用 **Android Studio**（或 IntelliJ IDEA）打开 `hebiace-campus/` 目录。
2. 首次打开会提示生成 Gradle Wrapper（本仓库未包含二进制 `gradle-wrapper.jar`，也可手动执行 `gradle wrapper --gradle-version 8.9`）。
3. 配置 SDK：在 `local.properties` 写 `sdk.dir=你的Android SDK路径`（Android Studio 一般自动生成）。
4. 环境要求：JDK 17、Android SDK Platform 35。
5. 点 Run 运行，或命令行：
   ```bash
   ./gradlew :composeApp:assembleDebug
   ```
   产物在 `composeApp/build/outputs/apk/debug/`。

> 说明：本机未配置 Android SDK，源码未经本机编译验证；如遇个别依赖版本或 API 名差异，按 IDE 提示微调即可。

## iOS 说明

- 共享代码已在 `iosMain` 写好；`MainViewController` 为入口。
- **待完善**（需 Mac + Xcode）：教务 RSA/AES 登录加密、BLE 扫描（CoreBluetooth）、Keychain 安全存储、内置 WebView 登录。
- 生成 `iosApp` Xcode 工程后可编译真机包（需自签）。

## 各模块接口（逆向成果，来自开源项目）

- **正方教务**：`csrftoken` + RSA/AES 加密密码登录 → `POST /kbcx/xskbcx_cxXsgrkb.html` 取 `kbList`。（参考 youwu / 通用正方脚本）
- **学习通**：`mooc1-api.chaoxing.com` 的 `backclazzdata`（课程）+ `workList/examList`（作业/考试，H5 正则解析）。（参考 xxt-Tool）
- **雨课堂**：`/v2/api/web/courses/list` + `logs/learn`（`type=5` 习题、`type=19` 作业）+ `leaf_info`。（参考 xxt-Tool）
- **趣智校园**：`v3-api.china-qzxy.cn`，登录（MD5 后10位大写/短信）、`downRate/closeOrder/queryUsing/consumeOrder` 等，BLE 广播名 `KLCXKJ-Water`。（参考 linyu 的 `API-qzxy.md`）
- **U净**：`phoenix.ujing.online/api/v1/`，`login`→`scanWasherCode`→`program/info`→`orders/create`→`payment/arguments`→`control/start|stop`。（参考 FlandreSY）

## 换学校适配

本项目硬编码了河北建筑工程学院。换学校主要改两处：

1. **教务**：`ZhengfangClient` 的 `host`/`basePath`（`AppState.jwHost()` 可在「我的」页改）；作息节次时间若与默认不同，需补 `SectionTime`。
2. **洗浴/洗衣**：趣智校园与 U净是跨校通用的第三方平台，通常无需改；若学校用了其它系统，替换对应 `data/qzxy` / `data/ujing` 客户端即可。

## 免责声明

仅供学习交流。登录与数据抓取均为**模拟正常客户端操作**，账号与接口存在变更风险，使用后果自负。严禁刷课、代做、绕过付费、攻击学校网站或售卖本项目。请于下载后 24 小时内删除，勿用于商业用途。

## 致谢

逆向与实现参考以下开源项目：`f3ngka0/youwu`、`siye4366/xxt-Tool`、`yehu-imei/linyu`、`amamiyakazuki/FlandreSY`、`ShiGuangSchedule/shiguangschedule`。各项目许可见其仓库。
