一、設計與動機（重點）

- 目的：在同一 fork 裡維持與 upstream（Signal）對齊，同時把你的 AI 功能（Breeze）以可選／隔離的方式納入並上 Google Play，並把 Play 發佈命名為 breezePlay（避免與 upstream 的 play 混淆）。
- 為何拆成 breeze-api + :breeze：
  - breeze-api：小型 contract（interface / DTO / registry），放置在可被 app compile 時使用的地方，提供型別安全與穩定契約，改動機率低，便於跟 upstream 合併。
  - :breeze：實作（AI 邏輯、UI、dialog、cache 等），只在 breezePlay 變體包含，能隔離第三方依賴與資源，減低與上游的衝突與風險。
- flavour 命名：不要重用 "play"（上游可能也用），改用 breezePlay 或 mtkPlay 更清楚，避免 CI / 溝通誤會。

二、具體檔案/目錄結構與範例（你可以直接貼用） 推薦的最小目錄（根目錄）：

- settings.gradle.kts
- breeze-api/
  - build.gradle.kts
  - src/main/java/com/mtkresearch/breeze/api/...
- breeze/
  - build.gradle.kts
  - src/main/java/com/mtkresearch/breeze/...
- app/
  - src/main/... （最少 glue / registry 在 main）
  - src/breezePlay/... （如果你不拆 module，也可把實作放這）

A. settings.gradle.kts（加入 module） 在根目錄的 settings.gradle.kts 加入： include(":breeze-api", ":breeze")

B. app/build.gradle.kts（重要：新增 breezePlay flavor 與依賴） 說明：repo 目前已用 Kotlin DSL；請在現有 productFlavors 區塊內新增 breezePlay。範例片段（放入 app/build.gradle.kts 的 productFlavors 中）：

```kotlin
productFlavors {
  // 既有的 flavors ...
  create("breezePlay") {
    dimension = "distribution"
    // 如果你要在 breezePlay 覆寫 applicationId，這裡可以加：
    // applicationId = "com.mtkresearch.securesms" // 或其它
    buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\"breezePlay\"")
    // 如果喜歡在 build 直接覆寫 app_name：
    // resValue("string", "app_name", "\"Breeze Messenger\"")
  }
}
```

在 dependencies 區塊加入：

- 如果拆成 api + impl：

```kotlin
implementation(project(":breeze-api"))
"breezePlayImplementation"(project(":breeze"))
```

- 如果你不拆 api（single-module 方案），把 breeze 實作放到 app/src/breezePlay/ 即可，無需新增依賴。

注意 selectableVariants：repo 裡有 selectableVariants 限制 build 變體（你的 app/build.gradle.kts 內）。請把 breezePlay 相關變體加入 selectableVariants（例如 breezePlayProdRelease、breezePlayProdDebug 等）。或在 androidComponents.beforeVariants 的邏輯中允許 breezePlay 變體。

C. breeze-api：最小 contract 與 registry（檔案範例） 路徑： breeze-api/src/main/java/com/mtkresearch/breeze/api/

- BreezeRegistry.kt

```kotlin
package com.mtkresearch.breeze.api

object BreezeRegistry {
  @Volatile var dataProvider: BreezeDataProvider? = null
  @Volatile var uiHook: BreezeUiHook? = null

  fun register(dataProvider: BreezeDataProvider?, uiHook: BreezeUiHook?) {
    this.dataProvider = dataProvider
    this.uiHook = uiHook
  }
}
```

- BreezeDataProvider.kt

```kotlin
package com.mtkresearch.breeze.api

data class ConversationSummary(val id: Long, val title: String?, val lastPreview: String?)
data class MessageSummary(val id: Long, val body: String?, val sender: String?, val timestamp: Long)

interface BreezeDataProvider {
  fun getConversationSummary(conversationId: Long): ConversationSummary?
  fun getMessageSummary(messageId: Long): MessageSummary?
}
```

- BreezeUiHook.kt
  
  ```kotlin
  package com.mtkresearch.breeze.api
  
  import android.content.Context
  import android.view.ViewGroup
  
  interface BreezeUiHook {
    fun attachToConversation(container: ViewGroup, conversationId: Long)
    fun shouldHideFeature(featureId: String): Boolean
    fun showDialog(context: Context, dialogId: String, args: Map<String,String> = emptyMap())
  }
  ```

D. :breeze module skeleton（build.gradle.kts） breeze/build.gradle.kts（最小）

```kotlin
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.kotlin.android)
}

android {
  namespace = "com.mtkresearch.breeze"
  compileSdk = (rootProject.extra["signalCompileSdkVersion"] as String).toIntOrNull() ?: 33
  defaultConfig {
    minSdk = (rootProject.extra["signalMinSdkVersion"] as Int)
  }
}

dependencies {
  implementation(project(":breeze-api")) // impl depends on api
  // 加你的 AI libs, e.g. implementation(libs.kotlinx.coroutines)
}
```

breeze/src/main/java/com/mtkresearch/breeze/BreezeEntry.kt （初始化點）

```kotlin
package com.mtkresearch.breeze

import android.content.Context
import com.mtkresearch.breeze.api.BreezeRegistry

object BreezeEntry {
  @JvmStatic
  fun initialize(context: Context) {
    // 註冊實作 (示例)
    BreezeRegistry.register(BreezeDataProviderImpl(context), BreezeUiHookImpl(context))
  }
}
```

實作類 BreezeDataProviderImpl / BreezeUiHookImpl 放在此 module。

E. app 端最小 glue（AppBreezeRegistration） 放在 app/src/main/java/...：

- AppBreezeDataProvider.kt（app 端透過現有 repo/DAO 做唯讀）
  
  ```kotlin
  package org.thoughtcrime.securesms.breeze
  
  import com.mtkresearch.breeze.api.BreezeDataProvider
  import com.mtkresearch.breeze.api.ConversationSummary
  import com.mtkresearch.breeze.api.MessageSummary
  
  class AppBreezeDataProvider(/* inject repos */) : BreezeDataProvider {
    override fun getConversationSummary(conversationId: Long): ConversationSummary? {
      // 呼叫 app 的現有 DAO / repository，以唯讀方式回傳
      return null
    }
    override fun getMessageSummary(messageId: Long): MessageSummary? = null
  }
  ```

- 在 Application.onCreate 中註冊（app/src/main/.../ApplicationContext.kt）
  
  ```kotlin
  import com.mtkresearch.breeze.api.BreezeRegistry
  
  class ApplicationContext : Application() {
    override fun onCreate() {
      super.onCreate()
      BreezeRegistry.register(AppBreezeDataProvider(/*...*/), null)
      // 如果 breeze module 存在且啟動，BreezeEntry.initialize(context) 會在 breeze module 的 init 或 play variant 的入口被呼叫
    }
  }
  ```

實作策略：app 提供 dataProvider；:breeze 提供 uiHook 與其 AI 實作，兩者透過 BreezeRegistry 連結。

F. app 中插 hook 的示例（ConversationActivity） 在 conversation 的創建處加入極小呼叫（app/src/main/...ConversationActivity）

```kotlin
val hook = BreezeRegistry.uiHook
hook?.let {
  it.attachToConversation(containerView as ViewGroup, conversationId)
}
if (hook?.shouldHideFeature("share_button") == true) {
  shareButton.visibility = View.GONE
}
```

注意：這些呼叫要盡量小且穩定，減少與 upstream 合併時的衝突。

三、遷移步驟與 Git 操作（實際操作指令）

1. 建一個工作分支（例）
   
   ```bash
   git checkout -b feat/breeze-skeleton
   ```

2. settings.gradle.kts 加 include(":breeze-api", ":breeze")，commit。
3. 建 breeze-api 與 breeze module 資料夾與檔案（copy 上面範例），commit。
4. 在 app/build.gradle.kts 的 productFlavors 裡新增 breezePlay，並在 dependencies 加入 implementation(project(":breeze-api")) 與 "breezePlayImplementation"(project(":breeze"))，commit。
5. 把 icon 與 app_name 移到 flavor（若你之前覆蓋在 main）：
   
   ```bash
   git checkout -b fix/move-icons-to-breezePlay
   mkdir -p app/src/breezePlay/res/mipmap-xxxhdpi
   # git mv app/src/main/res/mipmap-.../ic_launcher* app/src/breezePlay/res/mipmap-...
   git add -A
   git commit -m "Move launcher icons to breezePlay flavor resources"
   ```

6. 在 Application.onCreate 加入 BreezeRegistry 註冊和在需要處所加入 hook 呼叫（小改動），commit。
7. local build 測試：
   
   ```bash
   ./gradlew assembleBreezePlayProdDebug
   # 若 selectableVariants 限制，需要把 breezePlay 變體加入 selectableVariants
   ```

8. push branch 並開 PR 到 origin/main（或你想合併的 branch）。

四、CI / build 與 selectableVariants

- 要在 CI（GitHub Actions）加入 build matrix，至少包含：
  - upstream-like variant（保持能 build upstream）
  - breezePlay variant（assembleBreezePlayProdRelease / Debug）
- 你的 app/build.gradle.kts 有 selectableVariants 列表，請把 breezePlay 對應的變體名稱加入。例如列出： breezePlayProdRelease、breezePlayProdDebug 等（根據你的 product flavor + environment dimension 名稱組合）。
- 如果不想改 selectableVariants，每次本地測試可使用 assembleBreezePlayProdDebug 指令，但 CI 若用自定的 enabled variants，必須同步。

範例 GitHub Actions job（簡短示意）：

- job steps: checkout, cache gradle, ./gradlew assembleBreezePlayProdDebug, ./gradlew assembleProdRelease（或 assembleUpstream...）

五、合併 / 上游同步工作流程（建議流程）

- upstream remote
  
  ```bash
  git remote add upstream https://github.com/signalapp/Signal-Android.git
  git fetch upstream
  ```

- 保持兩個主分支：
  - upstream-main（只用來追上游，不包含 Breeze 改動）
  - origin/main（你的 Play 發行分支，包含 breezePlay 的改動）
- 同步上游（merge 或 rebase）：
  - git checkout origin/main
  - git fetch upstream
  - git merge upstream/main (或 rebase)
  - 解衝突（優先保留上游功能變更，將 Breeze 改動集中在 flavor/module）
- 若 API 需要改（breeze-api），請在同一 PR 中同時更新 app glue + :breeze 實作以確保 CI 綠燈；若是破壞性改動，採版本化或加兼容層。

六、測試 / QA / 安全 / 發佈注意

- 測試：
  - unit tests：breeze-api 提供測試 double；:breeze 有自己的 unit tests。
  - integration tests：在 CI 裡跑 assembleBreezePlayProdDebug 與自動化 UI tests（如果有）。
- 資料存取（唯讀）：
  - Breeze 不修改 core DB；app 提供只讀 provider。避免改 core schema，使用 message id 做關聯（例如在 Breeze module 自己的 table 存 messageId 與 metadata）。
- 隱私：
  - 若 Breeze 使用用戶資料進行 AI 推論，需處理 PII、合規、使用者同意、資料保留政策。紀錄在 README/PR 中並確保測試。
- 簽名 / Play Console：
  - applicationId 若與 origin/main 一致，視為同一 Play 應用；若要單獨上架，確保 applicationId 與 Play Console 設定一致、keystore 與上傳 keys 正確。CI secrets 管理 keystore。
- 資源 / manifest：
  - launcher icon 與 app_name 放到 flavor（app/src/breezePlay/res/...）。不要把 application-wide manifest 屬性放在 library :breeze。
- 版本號：
  - 保持 versionCode / versionName 的規則（若需要差異可用 flavor override 或 suffix）。

七、何時修改 breeze-api vs :breeze（重申）

- 改 breeze-api：當你要改契約（新增 dataProvider 方法、UI hook 介面、DTO）時。改 api 會影響 consumers（app + :breeze）。
- 改 :breeze：當你僅改 AI 演算法、UI 實作、資源、依賴時，且介面不變。

八、範例操作清單（一步一步，最小變更路徑）

1. 建分支 feat/breeze-skeleton。
2. include modules，建立 breeze-api + :breeze skeleton，commit。
3. 在 app/build.gradle.kts 新增 flavor breezePlay 並把 breeze module 加入 "breezePlayImplementation"，commit。
4. 實作 BreezeRegistry（放在 breeze-api）、AppBreezeDataProvider（放在 app/src/main）、BreezeEntry.initialize（放在 :breeze），commit。
5. 把 icon 與 strings 移到 app/src/breezePlay/res/，commit。
6. 本地 build: ./gradlew assembleBreezePlayProdDebug（或 assembleBreezePlayProdRelease）。
7. push branch，開 PR → CI 執行 build matrix → merge。
