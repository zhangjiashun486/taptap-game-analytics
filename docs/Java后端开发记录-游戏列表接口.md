# Java 后端开发记录：游戏列表 / 详情接口

**项目**：TapTap 游戏数据分析与智能推荐平台（HunuProjec2）  
**阶段**：项目开发 5.2（Java 后端）  
**本步目标**：让前端能够分页浏览 10,219 款游戏、按名称搜索、查看单款游戏详情（含标签）  
**学习方式**：先看“一、思路（要做什么）”，再看“二、代码（怎么做的）”，最后看“三、实测结果”

---

# 一、思路：这一步要做什么

## 1.1 需求

前端（或 Postman）需要两个接口：

| 接口 | 作用 | 参数 |
| --- | --- | --- |
| `GET /api/game/page` | 分页返回游戏列表，支持搜索和排序 | `page`（页码）、`limit`（每页条数）、`keyword`（搜索词）、`sort`（排序方式） |
| `GET /api/game/{gameId}` | 返回单款游戏详情 + 标签列表 | 路径里的 gameId |

两个接口都**需要登录后携带 token**（JWT 拦截器统一校验）。

## 1.2 请求是怎么一步步走完的（先记这张图）

```
前端/Postman
   │  GET /api/game/page?page=1&limit=5      （请求头带 Authorization: Bearer <token>）
   ▼
JwtInterceptor（拦截器）
   │  检查 token：没有/无效 → 直接返回 401；有效 → 放行，并把 userId 放进 request
   ▼
GameController（控制层）
   │  接收参数（page/limit/keyword/sort），调用 Service
   ▼
GameServiceImpl（业务层）
   │  ① 参数保护（页码/条数/空搜索词）
   │  ② 排序白名单（防 SQL 注入）
   │  ③ 先 count 总数，再查当前页
   ▼
GameDao（数据访问层，MyBatis）
   │  把 @Select 注解里的 SQL 发给 MySQL
   ▼
MySQL（taptap_course.t_game / t_game_tag / t_tag）
   │  返回结果集
   ▼
MyBatis 把结果集变成 List<Game>
   ▼
GameController 用 Result 包装成 {code,message,data}
   ▼
前端收到 JSON
```

## 1.3 三个关键设计点

| 设计点 | 怎么做 | 为什么 |
| --- | --- | --- |
| 分页 | `offset = (page - 1) * limit`，SQL 用 `LIMIT offset, limit` | 避免一次返回 1 万条数据，前端只拿当前页 |
| 搜索 | `title LIKE CONCAT('%', #{keyword}, '%')` | 模糊匹配游戏名 |
| 排序 | 前端只传 `fans/rating/review/release`，Service 用**白名单**翻译成固定 SQL | 防止前端传恶意 SQL（SQL 注入） |

---

# 二、代码：每一步对应哪个文件

## 2.1 文件清单

| 文件 | 层 | 作用 |
| --- | --- | --- |
| `entity/Game.java` | 实体 | 数据库一行 → Java 对象 |
| `dao/GameDao.java` | 数据访问 | 写 SQL：分页、统计、详情、标签 |
| `service/GameService.java` | 业务接口 | 定义业务方法 |
| `service/impl/GameServiceImpl.java` | 业务实现 | 参数保护、排序白名单、分页计算 |
| `controller/GameController.java` | 控制层 | 接收 HTTP 请求，返回统一 Result |

## 2.2 实体：Game.java（数据库一行 → Java 对象）

```java
@Data                       // Lombok：自动生成 getter/setter
public class Game {
    private Long gameId;             // game_id
    private String title;            // title
    private BigDecimal ratingScore;  // rating_score
    private BigDecimal latestScore;  // latest_score
    private Integer reviewCount;     // review_count
    private Long fansCount;          // fans_count
    private Long hitsTotal;          // hits_total
    private Integer wishCount;       // wish_count
    private Integer reserveCount;    // reserve_count
    private String price;            // price
    private String platform;         // platform
    private LocalDateTime releasedTime; // released_time
    private String developer;        // developer
    private String intro;            // intro
    private String iconUrl;          // icon_url
}
```

> 记住：**数据库列名是下划线（rating_score），Java 字段是驼峰（ratingScore）**。MyBatis 查询时用 `AS ratingScore` 做别名，或在配置里开启 `map-underscore-to-camel-case=true`（两者我们都做了）。

## 2.3 数据访问：GameDao.java（写 SQL 的地方）

```java
@Mapper                                   // 告诉 MyBatis：这是数据库访问接口
public interface GameDao {

    @Select("<script>" +
            "SELECT game_id AS gameId, title, rating_score AS ratingScore, ... " +
            "FROM t_game WHERE 1 = 1 " +
            "<if test='keyword != null and keyword != \"\"'>" +      // 动态 SQL：有搜索词才拼 LIKE
            "  AND title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "ORDER BY ${orderBy} " +                                  // 排序（值由 Service 白名单控制）
            "LIMIT #{offset}, #{limit}" +                             // 分页
            "</script>")
    List<Game> selectPage(@Param("keyword") String keyword,
                          @Param("orderBy") String orderBy,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM t_game WHERE 1 = 1 " +
            "<if test='keyword != null and keyword != \"\"'>AND title LIKE CONCAT('%', #{keyword}, '%')</if>" +
            "</script>")
    long count(@Param("keyword") String keyword);

    @Select("SELECT ... FROM t_game WHERE game_id = #{gameId}")
    Game findById(@Param("gameId") Long gameId);

    @Select("SELECT t.tag_name FROM t_game_tag gt JOIN t_tag t ON gt.tag_id = t.tag_id " +
            "WHERE gt.game_id = #{gameId} ORDER BY t.tag_name")
    List<String> findTags(@Param("gameId") Long gameId);
}
```

**两个必须理解的区别**：

| 写法 | 含义 | 安全性 |
| --- | --- | --- |
| `#{keyword}` | 预编译参数（相当于 JDBC 的 `?`） | 安全，防 SQL 注入 |
| `${orderBy}` | 直接拼接到 SQL 文本里 | **危险**！所以只能拼“白名单里的固定字符串” |

## 2.4 业务层：GameServiceImpl.java（真正的规则在这里）

```java
private static final Map<String, String> SORT_WHITELIST = Map.of(
        "default", "fans_count DESC",
        "fans", "fans_count DESC",
        "rating", "rating_score DESC",
        "review", "review_count DESC",
        "release", "released_time DESC"
);

public Map<String, Object> page(int page, int limit, String keyword, String sort) {
    // ① 参数保护
    if (page < 1) page = 1;
    if (limit < 1) limit = 20;
    if (limit > 100) limit = 100;                 // 每页最多 100 条

    // ② 空搜索词统一转成 null
    String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

    // ③ 排序白名单（不在名单里就用默认排序）
    String orderBy = SORT_WHITELIST.getOrDefault(sort == null ? "default" : sort,
                                                SORT_WHITELIST.get("default"));

    // ④ 先查总数，再查当前页
    long total = gameDao.count(kw);
    int offset = (page - 1) * limit;
    List<Game> list = gameDao.selectPage(kw, orderBy, offset, limit);

    // ⑤ 组装分页结构 { list, total, page, limit }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("list", list);
    data.put("total", total);
    data.put("page", page);
    data.put("limit", limit);
    return data;
}
```

**分页公式举例**（每页 5 条）：

| 页码 | offset | SQL |
| --- | --- | --- |
| page=1 | 0 | `LIMIT 0, 5` |
| page=2 | 5 | `LIMIT 5, 5` |
| page=3 | 10 | `LIMIT 10, 5` |

## 2.5 控制层：GameController.java（接收请求、返回结果）

```java
@RestController                    // 返回 JSON 的控制器
@RequestMapping("/api/game")       // 这一组接口的前缀
public class GameController {

    private final GameService gameService;   // 依赖注入（构造器方式）

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/page")           // GET /api/game/page
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,          // 页码，默认 1
            @RequestParam(defaultValue = "20") int limit,        // 每页条数，默认 20
            @RequestParam(required = false) String keyword,      // 搜索词，可不传
            @RequestParam(defaultValue = "default") String sort) // 排序，可不传
    {
        return Result.success(gameService.page(page, limit, keyword, sort));
    }

    @GetMapping("/{gameId}")       // GET /api/game/206776
    public Result<Map<String, Object>> detail(@PathVariable Long gameId) {
        Map<String, Object> data = gameService.detail(gameId);
        return data == null ? Result.error(404, "游戏不存在") : Result.success(data);
    }
}
```

**注解小词典**：

| 注解 | 作用 |
| --- | --- |
| `@RestController` | 这个类处理 HTTP 请求，返回值自动转成 JSON |
| `@RequestMapping("/api/game")` | 类里所有接口的公共前缀 |
| `@GetMapping("/page")` | 处理 GET `/api/game/page` |
| `@RequestParam` | 取 URL 里的 `?page=1&limit=5` 参数 |
| `@PathVariable` | 取路径里的变量，如 `/api/game/{gameId}` 的 206776 |
---

# 三、怎么调用（含真实请求与响应）

## 3.1 第一步：先登录拿 token

```powershell
$base = "http://127.0.0.1:18080"   # 正式端口是 8080；测试时因 Steam 占用改为 18080
$login = Invoke-RestMethod -Method Post -Uri "$base/api/user/login" `
    -ContentType "application/json" `
    -Body '{"username":"stu0912a","password":"123456"}'
$token = $login.data.token
```

返回（节选）：

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9....",
    "userId": 1,
    "username": "stu0912a",
    "nickname": "测试学生",
    "role": "USER"
  }
}
```

## 3.2 游戏列表（分页 + 排序）

```powershell
Invoke-RestMethod -Uri "$base/api/game/page?page=1&limit=5" `
    -Headers @{ Authorization = "Bearer $token" }
```

真实响应（只展示前 3 条）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      { "gameId": 2301,  "title": "王者荣耀", "ratingScore": 5.00, "fansCount": 98297484, "developer": "腾讯" },
      { "gameId": 70056, "title": "和平精英", "ratingScore": 7.10, "fansCount": 85015834, "developer": "腾讯" },
      { "gameId": 58881, "title": "香肠派对", "ratingScore": 8.60, "fansCount": 43337283, "developer": "真有趣游戏" }
    ],
    "total": 10219,
    "page": 1,
    "limit": 5
  }
}
```

## 3.3 搜索（keyword）

```powershell
Invoke-RestMethod -Uri "$base/api/game/page?page=1&limit=5&keyword=原神" `
    -Headers @{ Authorization = "Bearer $token" }
```

真实响应（total=2）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      { "gameId": 168332, "title": "原神", "ratingScore": 7.90, "fansCount": 38909501 },
      { "gameId": 923906, "title": "原神", "ratingScore": null, "fansCount": 86 }
    ],
    "total": 2, "page": 1, "limit": 5
  }
}
```

> 说明：搜索是“包含匹配”，同名/近似名游戏都会出来；没有评分的游戏 `ratingScore` 为 null（数据里本来就有 1,879 款无评分游戏）。

## 3.4 游戏详情（含标签）

```powershell
Invoke-RestMethod -Uri "$base/api/game/206776" -Headers @{ Authorization = "Bearer $token" }
```

真实响应（节选）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "game": { "gameId": 206776, "title": "蛋仔派对", "ratingScore": 7.8,
              "fansCount": 38701637, "developer": "网易游戏" },
    "tags": ["休闲", "可爱", "多人联机", "派对游戏", "竞技"]
  }
}
```

## 3.5 不带 token（应被拦截）

```powershell
Invoke-WebRequest -Uri "$base/api/game/page?page=1&limit=5" -SkipHttpErrorCheck
```

真实响应：

```
HTTP 401
{"code":401,"message":"未登录或缺少 token","data":null}
```

---

# 四、实测结果汇总

| 测试项 | 结果 |
| --- | --- |
| 分页列表 | ✅ total=10219，第 1 页 5 条，按关注数排序 |
| 搜索“原神” | ✅ total=2（168332 原神 7.9 / 923906 无评分） |
| 游戏详情 | ✅ 蛋仔派对：评分 7.8、关注 38,701,637、5 个标签 |
| 未登录访问 | ✅ HTTP 401 + 统一 JSON |
| 数据库 | ✅ MySQL 5.6 + 驱动 5.1.49，10,219 条数据 |

---

# 五、常见问题（FAQ）

**Q1：为什么接口必须带 token？**  
《项目规则》要求除白名单（登录/注册/健康检查）外都要登录。前端登录成功后把 token 存到 `localStorage`，之后每次请求加请求头：

```
Authorization: Bearer <token>
```

**Q2：为什么返回结构是 `{code, message, data}`？**  
统一响应格式，前端只判断 `code == 200`；`data` 里放业务数据。分页数据固定为 `{list, total, page, limit}`。

**Q3：排序为什么不直接拼前端传的值？**  
因为 SQL 里 `ORDER BY ${orderBy}` 是**字符串拼接**，直接拼前端值可能被注入恶意 SQL。我们用**白名单**：前端只能传 `fans/rating/review/release`，服务端翻译成固定排序语句。

**Q4：`#{keyword}` 和 `${orderBy}` 有什么区别？**  
`#{}` 是预编译参数（安全，相当于 JDBC 的 `?`）；`${}` 是直接拼接（危险）。能用 `#{}` 就用 `#{}`，不得不用 `${}` 时必须做白名单。

**Q5：分页是怎么算的？**  
`offset = (page - 1) * limit`，SQL 用 `LIMIT offset, limit`。例如 page=2、limit=5 → `LIMIT 5, 5`。

**Q6：为什么 limit 最大 100？**  
防止前端一次请求过多数据（如 limit=10000），拖慢数据库和接口。

**Q7：表字段和 Java 字段对不上怎么办？**  
数据库是下划线（`rating_score`），Java 是驼峰（`ratingScore`）。SQL 里用 `AS ratingScore` 别名，同时配置了 `map-underscore-to-camel-case=true`。

---

# 六、下一步计划

| 优先级 | 接口 | 说明 |
| --- | --- | --- |
| 1 | `POST /api/behavior` | 记录收藏/玩过/评分等用户行为（推荐系统的数据来源） |
| 2 | `GET /api/recommend` | 基于“用户行为 + 聚类结果”的个性化推荐 |
| 3 | `POST /api/qa/ask` | 数据问答（先用规则/SQL，后续可接大模型） |
| 4 | 管理端统计接口 | 采集/清洗/聚类/用户行为可视化数据 |
| 5 | 前端对接 | 用 admin_server / client_server 调用以上接口 |

---

# 附：本步涉及的文件

```
base_server/src/main/java/org/example/base_server/
├─ entity/Game.java                      游戏实体
├─ dao/GameDao.java                      MyBatis 数据访问（分页/搜索/详情/标签）
├─ service/GameService.java              业务接口
├─ service/impl/GameServiceImpl.java     参数保护 + 排序白名单 + 分页
└─ controller/GameController.java        两个 HTTP 接口
```

*本文档记录的是真实运行结果（2026-09-12），用于课程报告、答辩讲解与后续学习。*