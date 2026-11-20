# 域设计：storage（图片上传）

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口与失败语义，不重复状态。

> 范围：帖子配图上传与静态访问。
> 不含：视频、第三方图床、CDN、文件秒传。

## 1. 调用关系

```
FileController
 └─ POST /api/v1/files/images (multipart) → StorageService.save(...)

StorageService
 ├─ 类型白名单 + 魔数校验（前 12 字节）
 ├─ 大小与数量上限
 ├─ LocalStorageService      写到 roam.upload.dir，返回 /files/...
 └─（P2）OssStorageService   切 OSS 只需换实现

StaticResourceConfig
 └─ /files/** → file:{roam.upload.dir}    只读静态访问，不走鉴权
```

`StorageService` 声明在 service 层，当前只有一个本地实现；按基线"出现第二个实现才抽接口"的例外处理——这里抽接口是因为 OSS 切换是明确的后续需求，且接口只有 3 个方法。

## 2. 接口

```java
public record StoredFileVO(String url, Integer width, Integer height) {}

public interface StorageService {
    StoredFileVO save(byte[] bytes, String originalName, String contentType, long uid);
}
```

- URL 形如 `/files/2026-11/{uuid}.png`，是站内相对路径；
- 宽高用 `ImageIO` 读取，读不到（如 webp）返回 null，不报错。

## 3. 失败语义

| 场景 | 返回 |
|---|---|
| 未登录 | `401` |
| 文件为空 | `4001` |
| 类型不在白名单（jpeg/png/webp） | `4001` |
| 扩展名是图片但魔数不符（伪装的 svg/html） | `4001` |
| 超过 5MB | `4001` |
| 磁盘写入失败 | `500`，日志带原文件名与非敏感信息 |

**拒绝 svg**：svg 可携带脚本，作为 `<object>` 打开会执行。

## 4. 配置

```yaml
roam:
  upload:
    dir: ./uploads
    allowed-prefix: /files/
    max-size-mb: 5
```

## 5. 与发帖的约束

发帖时 `media[].url` 必须以 `roam.upload.allowed-prefix` 开头，否则 `4001`——防止把任意外链或 `data:` 协议塞进帖子。

## 6. 测试接缝

| 接缝 | 用例 |
|---|---|
| HTTP | `上传_未登录_401`、`上传_png_返回url和尺寸`、`上传_svg伪装成png_4001`、`上传_超过5MB_4001` |
| 发帖 | `发帖_媒体URL非本站前缀_4001` |

## 协调（谁给我数据 / 我给谁数据）

- **拿**：上传者 uid 仅用于日志，不落库。
- **给**：返回站内相对 URL（`/files/…`），post 域发帖时校验前缀，防外链与 `data:` 协议。
- **时序**：先落盘再返回 URL；发帖时只存 URL，不复制文件。
- **失败**：磁盘写失败 500；类型/大小/魔数不符 4001；图片尺寸读不到返回 null，不报错。
