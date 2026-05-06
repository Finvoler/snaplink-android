- 保存 Account ID、Access Key ID、Secret Access Key、Bucket 名称、公开访问域名
# R2 Image Bed Android

一个原生 Kotlin Android Studio 项目，用于把 Cloudflare R2 当作个人图床使用。

## 已实现

- 保存 Account ID、Access Key ID、Secret Access Key、Bucket 名称
- 在 R2 Bucket 中创建多级文件夹，例如 `cat/`、`travel/2026/`
- 相册批量选图上传、单张上传、相机拍照上传
- 上传时选择目标文件夹，上传后直链自动包含完整路径
- 文件夹/文件浏览、删除文件、递归删除文件夹
- 文件列表缩略图按小尺寸懒加载，减少滚动时的图片解码压力
- 点击复制完整直链，格式固定为：

- 直链格式：
  `https://{公开访问域名}/{文件夹路径}/{文件名}`
  例如 `https://pub-1789d365161d4e099c31faa3afa94e8d.r2.dev/四谎/IMG_20260506_150317.jpg`
  配置时也可以直接粘贴一整条现成图片链接，应用会自动提取前面的域名根；不同文件夹会通过对象 key 自动追加到路径中。

## 打开方式

1. 使用 Android Studio 打开项目根目录。
2. 如果本机还没有 Android SDK，请先准备 SDK 根目录，并至少包含 `platform-tools`、`platforms/android-35`、`build-tools/35.0.0`。
3. 等待 Gradle 同步完成。
4. 在设备或模拟器上运行 `app` 模块。

## 若需离线补齐依赖

把下面两个文件放到 [r2-image-bed-android/.downloads](r2-image-bed-android/.downloads)：

- `commandlinetools-win-14742923_latest.zip`
- `gradle-8.7-bin.zip`

然后运行 [r2-image-bed-android/scripts/setup-local-android-sdk.ps1](r2-image-bed-android/scripts/setup-local-android-sdk.ps1)。

脚本会自动：

- 解压命令行 SDK 到项目内的 `android-sdk`
- 安装 `platform-tools`、`platforms;android-35`、`build-tools;35.0.0`
- 复用项目里的 [r2-image-bed-android/local.properties](r2-image-bed-android/local.properties) 作为 SDK 路径

## 环境要求

- Android Studio Koala 或更新版本
- Android SDK 35
- JDK 17
- 最低支持 Android 8.0 (API 26)

## 说明

- R2 使用 S3 兼容接口，App 内部通过 SigV4 直接签名请求。
- 文件夹在对象存储里通过以 `/` 结尾的对象 key 来表示。
- 删除文件夹时会递归删除其下所有对象。
- 当前工作区机器未检测到 Android SDK；Gradle 8.7 缓存也只有未完成的 `.part` 文件，所以若要在这台机器直接出 APK，还需要先补齐 SDK 和完整 Gradle 分发包。