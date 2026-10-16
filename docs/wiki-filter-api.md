# 库街区 WIKI 的分类 / 筛选接口（实测记录）

> 探明时间：2026-10-15（P11 补 26）
> 目的：用户要求图鉴按官方 WIKI 的样子做**筛选**（稀有度 / 属性 / 武器 / 实装版本 / 风格定位）。
> ✅ **结论：筛选所需的数据真实存在，不需要任何伪造。**

---

## 一、请求格式（★ 这是本文件最值钱的一条）

```
POST https://api.kurobbs.com/wiki/core/catalogue/item/getPage
Content-Type: application/x-www-form-urlencoded      ← ★ 不是 JSON！
source: h5
wiki_type: 9
devcode: <任意 32 位十六进制>

body: catalogueId=1105&pageNo=1&pageSize=3
```

★ **JSON body 会返回 `code = 500`。** 我第一次探测就是发 JSON 失败的，
表现是"接口挂了"，实际只是格式不对。

★ **`pageSize` 似乎被忽略** —— 传 `pageSize=3` 实际返回了 **65 条**（该分类全量）。
不要用返回条数反推总数，`results.total` 才是总数。

---

## 二、响应中与筛选相关的三个部分

### 1. `data.results.records[]` —— 每个词条

关键字段在 `content` 里：

| 字段 | 例 | 说明 |
|---|---|---|
| `title` | `"心"` | 名称 |
| `star` | `"5"` | **字符串**，不是数字 |
| `skillAttr` | `"3"` | 属性（数值编码，需另找映射） |
| **`relateTagIds`** | `["210","214","532","1058","758","760","764"]` | ★ **筛选的依据** |
| `contentUrl` | `https://prod-alicdn-community.kurobbs.com/…png` | 立绘 |
| `cornerMark` / `cornerMarkUrl` | `false` / `""` | 左上角「新/复刻/预」角标 |
| `customBgUrl` | `""` | 卡片自定义背景（稀有度底色可能来自这里） |
| `showLeftTopIcon` / `showTeaserIcon` | `true` | 图标显示开关 |
| `type` | `"vertical-figure"` | 卡片版式类型 |

### 2. `data.tagTree` —— ★ **筛选条件树**

它**不是数组**，是**当前分类这个节点本身**（`id = 1105`，`name = "共鸣者"`），
筛选分组在它的 `children` 里：

```
tagTree { id:1105, name:"共鸣者", children: [
    { id:209, name:"稀有度", level:1, children: [
        { id:210, name:"五星", parentId:209, level:2, children:null },
        { id:211, name:"四星", parentId:209, level:2, children:null },
    ]},
    … 其余分组（属性 / 武器 / 实装版本 / 风格定位）
]}
```

★ **传 `catalogueId` 不同，`tagTree` 就不同** —— 它是**随分类走的**，
所以筛选面板必须**从接口拿**，不能写死那五组。
（例如「武器投影」的筛选条件显然和「共鸣者」不一样。）

### 3. 筛选怎么算

```
选中一组 tag id → 保留 满足 relateTagIds ∩ 选中集合 ≠ ∅ 的词条
```

- 同一分组内多个选中 = **或**（五星 ∪ 四星）
- 不同分组之间 = **与**（五星 ∩ 导电）

★ 这层语义要**自己实现**，接口不代做 —— 或者把 tag 传给接口让它过滤（未验证）。

---

## 三、实测样本（共鸣者，2026-10-15）

| 词条 | star | skillAttr | relateTagIds |
|---|---|---|---|
| 心 | 5 | 3 | 210, 214, 532, 1058, 758, 760, 764 |
| 千咲 | 5 | 7 | 210, 218, 528, 863, 763, 764 |
| 尤诺 | 5 | 2 | 210, 213, 529, 829, 759, 761, 763 |

三条都含 `210`（五星）→ 与「稀有度 = 五星」的 tag 一致 ✓ **机制自洽**。

---

## 四、待办（尚未实现）

1. `tagTree` 的完整分组名与叶子名（本轮只确认了第一个分组是「稀有度」）
2. `skillAttr` 数值 → 属性名（气动/导电/冷凝/热熔/衍射/湮灭）的映射来源
   ★ **不得猜测**。可以从 `tagTree` 的「属性」分组反查，或另找接口
3. 卡片版式升级：立绘铺满 + 稀有度底色 + 星行 + 角标 + 属性图标 + 底部名称条
4. 筛选面板 UI（底部弹层，分组多选 + 取消/确认）

## 五、明确不做的事

- ❌ 不写死筛选分组 —— 必须来自 `tagTree`
- ❌ 不猜 `skillAttr` 数值含义 —— 没有可靠映射就不显示属性图标
- ❌ 不用假数据填充筛选结果
