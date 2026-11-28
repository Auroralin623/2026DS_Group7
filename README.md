# MovieFinder

主題式電影搜尋引擎(2026 資料結構 Group 7)。輸入 `cat`、`cooking` 這類主題,系統會透過 Google 找出候選網站,爬取子網頁、建立網站樹並重新計分排序。

- 分工與時程:[docs/TEAM_PLAN.md](docs/TEAM_PLAN.md)
- 架構與介面約定:[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- 成效評估資料:[eval/README.md](eval/README.md)

## 開始工作前

**不要直接在 `main` 上寫程式。** 每項工作都要先開分支,完整流程見 [docs/TEAM_PLAN.md](docs/TEAM_PLAN.md#每次開工的流程)。

```bash
git checkout main
git pull
git checkout -b feature/模組-內容
```

## 快速開始

只需要安裝 JDK 17 以上版本,不必安裝 Maven。Windows 請把 `./mvnw` 換成 `mvnw.cmd`。

```bash
# 跑全部測試
./mvnw test

# 用假資料啟動網站(不需要 API key),再開 http://localhost:8080
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

mock 模式只顯示假資料,不會執行後端組員的程式。後端的測試方式見 [怎麼測試自己的部分](docs/TEAM_PLAN.md#怎麼測試自己的部分)。

不加 mock profile 啟動就是完整的真實搜尋,需要先設定 `TAVILY_API_KEY`(見下方)。一次搜尋會讀取每個網站和它的子網頁,大約要等一分鐘。

## Tavily 金鑰

**每個人用自己的金鑰。** 金鑰只設在自己電腦的環境變數裡,不寫進任何檔案,所以永遠不會被 commit,別人也拿不到。

| 情況 | 需要金鑰嗎 |
|---|---|
| 跑測試(`./mvnw test`) | 不需要,測試不連網 |
| mock 模式 | 不需要 |
| 真實搜尋(hw8 模式、完整網站) | 需要自己的金鑰 |
| 部署上線 | 設在雲端平台的環境變數,由 B 和全組決定用誰的金鑰 |

申請方式:到 https://app.tavily.com 註冊,免費,不需要信用卡,格式類似 `tvly-xxxx`。免費方案每人每月 1,000 次,每個人各自申請,額度也是分開算的。

**不要把金鑰貼到群組或 GitHub 上。** 如果不小心外流,到 Tavily 的 Dashboard 刪掉舊金鑰、建立新的。

## 使用真實搜尋

```bash
export TAVILY_API_KEY=你的金鑰

# HW8 版本:直接顯示 Tavily 結果(不計分)
./mvnw spring-boot:run -Dspring-boot.run.profiles=hw8

# 完整版本:等 C 的搜尋流程完成後使用
./mvnw spring-boot:run
```

Windows 的 PowerShell 請用 `$env:TAVILY_API_KEY="你的金鑰"` 設定。環境變數只對目前的終端機視窗有效,開新視窗要重新設定。

**不要把 API key 寫進任何會 commit 的檔案。**

## Stage 1 / Stage 2 命令列展示

待 Member A、B、E 的部分完成後即可使用:

```bash
# Stage 1:評分單一網頁
./mvnw -q spring-boot:run -Dspring-boot.run.main-class=edu.nccu.moviefinder.cli.StageDemo \
  '-Dspring-boot.run.arguments=page "movie:3,director:2" https://example.com'

# Stage 2:連同子網頁評分,並印出網站樹
./mvnw -q spring-boot:run -Dspring-boot.run.main-class=edu.nccu.moviefinder.cli.StageDemo \
  '-Dspring-boot.run.arguments=site "movie:3,director:2" https://example.com'
```

## 專案結構

```
src/main/java/edu/nccu/moviefinder/
├── controller/   SearchController
├── service/      SearchService, MockSearchService, KeywordExpander, GoogleQuery,
│                 SubLinkCrawler, PagePrefetcher, WordCounter, Ranker,
│                 Hw8SearchService(HW8 暫時版本)
├── model/        Keyword, KeywordList, WebPage, WebNode, WebTree, SearchResult
└── cli/          StageDemo(Stage 1、2 展示)
src/main/resources/
├── templates/    index.html, result.html, compare.html
├── keywords.csv  電影領域關鍵字與權重
└── stopwords.txt 找新關鍵字時要排除的字
src/test/         每個模組的驗收測試
eval/             固定測試查詢與人工評分資料
stage0/           HW3 原始作業(保留存檔,不參與建置)
```
