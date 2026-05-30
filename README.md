<div align="center">
  <h1>MPFM · 多协议文件挂载平台</h1>
  <p><strong>让 local / sftp / webdav 在一套文件体验里协同工作</strong></p>
  <p>
    <img alt="typing intro" src="https://readme-typing-svg.demolab.com?font=Fira+Code&size=18&pause=1400&color=3B82F6&center=true&vCenter=true&width=780&lines=One+API+Surface+for+Multi-Protocol+File+Operations;Capability-Driven+Routing%2C+Not+Protocol+if%2Felse+Sprawl;Consistent+List%2FRead%2FWrite%2FPreview+Experience" />
  </p>
  <p>
    <img alt="Java 17+" src="https://img.shields.io/badge/Java-17%2B-007396?style=flat-square&logo=openjdk&logoColor=white" />
    <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-Backend-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
    <img alt="React + TypeScript" src="https://img.shields.io/badge/React%20%2B%20TypeScript-Frontend-3178C6?style=flat-square&logo=typescript&logoColor=white" />
    <img alt="Protocols" src="https://img.shields.io/badge/Protocols-local%20%7C%20sftp%20%7C%20webdav-4A5568?style=flat-square" />
  </p>
</div>

<p align="center">
  <a href="#-项目定位">项目定位</a> ·
  <a href="#-核心能力">核心能力</a> ·
  <a href="#-快速开始">快速开始</a> ·
  <a href="#-开发提示">开发提示</a> ·
  <a href="#-修订记录">修订记录</a>
</p>

---

## 项目定位
<h3>What is MPFM</h3>
<p>
  MPFM 是一个面向多协议文件访问的统一平台，聚焦在同一业务域下整合
  <code>local / sftp / webdav</code> 的文件能力。
</p>

<h3>Why MPFM</h3>
<p>
  解决“同一业务要接入多协议”时的语义分裂问题：上层使用统一链路，协议差异下沉到驱动层。
</p>

## 核心能力
<h3>Feature Highlights</h3>
<ul>
  <li><strong>统一文件主链路:</strong> <code>list / read / write / preview / download</code> 一套语义覆盖多协议。</li>
  <li><strong>能力分发 + 驱动下沉:</strong> 协议差异集中在 <code>application/driver/**</code>，避免上层堆叠协议分支。</li>
  <li><strong>不支持能力显式失败:</strong> 返回统一错误码（如 <code>CAPABILITY_NOT_SUPPORTED</code>），不做沉默降级。</li>
</ul>

## 快速开始
<details>
  <summary><strong>1) 检查环境与依赖</strong></summary>

```shell
# 检查 PostgreSQL
postgres --version

# 检查 Redis
redis --version
```

如未安装依赖，可使用：

```shell
docker compose up -d
```
</details>

<details>
  <summary><strong>2) 配置环境变量</strong></summary>

```shell
cp .env.example .env
```

然后手动编辑 `.env`。

数据库这一组建议至少确认下面这些值，尤其是本地 PostgreSQL 地址、账号密码和连接池参数：

```env
MPFM_DB_URL=jdbc:postgresql://localhost:5432/mpfm_dev
MPFM_DB_USERNAME=postgres
MPFM_DB_PASSWORD=请替换为你的数据库密码
MPFM_DB_POOL_MIN_IDLE=5
MPFM_DB_POOL_MAX_SIZE=20
MPFM_DB_AUTO_COMMIT=false
MPFM_DB_TX_ISOLATION=TRANSACTION_READ_COMMITTED
MPFM_DB_POOL_NAME=mpfm-hikari
MPFM_DB_VALIDATION_TIMEOUT_MS=3000

MPFM_TEST_DB_URL=jdbc:postgresql://localhost:5432/mpfm_test
MPFM_TEST_DB_USERNAME=postgres
MPFM_TEST_DB_PASSWORD=请替换为你的测试库密码
MPFM_TEST_DB_POOL_MIN_IDLE=1
MPFM_TEST_DB_POOL_MAX_SIZE=5
MPFM_TEST_DB_AUTO_COMMIT=false
MPFM_TEST_DB_TX_ISOLATION=TRANSACTION_READ_COMMITTED
MPFM_TEST_DB_POOL_NAME=mpfm-test-hikari
```

启用 HTTPS（自签名证书）时，必须至少配置以下变量：

```env
MPFM_SERVER_PORT=8443
MPFM_TLS_ENABLED=true
MPFM_TLS_KEYSTORE_PATH=file:./backend/certs/keystore.p12
MPFM_TLS_KEYSTORE_TYPE=PKCS12
MPFM_TLS_KEYSTORE_PASSWORD=请替换为你的口令
MPFM_TLS_KEY_ALIAS=mpfm-local
MPFM_TLS_ENABLED_PROTOCOLS=TLSv1.3,TLSv1.2
```

前端也要同步后端地址（`new_frontend/.env` 或 `.env.local`）：

```env
VITE_BACKEND_ORIGIN=https://localhost:8443
VITE_DEV_BACKEND_TARGET=https://localhost:8443
```

如果前端本地也要启用 HTTPS（`https://localhost:5173`），在 `new_frontend/.env` 配置：

```env
VITE_DEV_HTTPS=true
VITE_DEV_HTTPS_PFX_FILE=./certs/frontend-dev.p12
VITE_DEV_HTTPS_PFX_PASSPHRASE=请替换为你的证书口令
```

说明：
- 前端开发服务支持两种证书方式：`PFX/P12` 或 `PEM key+cert`。
- Windows 场景推荐先用 `PFX/P12`。
</details>

<details>
  <summary><strong>3) 生成后端自签名证书（Windows / PowerShell）</strong></summary>

```powershell
.\scripts\ps1\gen-backend-dev-cert.ps1
```

脚本会自动完成：
- 生成 `backend/certs/keystore.p12`
- 输出证书关键校验信息（别名、有效期、SHA256 指纹、SAN）
- 运行时提示手动输入 `StorePass`（不回显）

你也可以手动复核：

```powershell
keytool -list -v -keystore .\backend\certs\keystore.p12 -storetype PKCS12
```
</details>

<details>
  <summary><strong>4) 导入后端证书到 Windows 受信任根（必做）</strong></summary>

```powershell
keytool -exportcert `
  -alias mpfm-local `
  -keystore .\backend\certs\keystore.p12 `
  -storetype PKCS12 `
  -file .\backend\certs\mpfm-local.cer `
  -rfc
```

导入步骤（`Win + R` -> `mmc`）：
- 文件 -> 添加/删除管理单元 -> 证书 -> 计算机帐户 -> 本地计算机
- 展开“证书(本地计算机)” -> “受信任的根证书颁发机构” -> “证书”
- 右键“证书” -> 所有任务 -> 导入，选择 `backend/certs/mpfm-local.cer`

可选：导入后重启 `WebClient` 服务，避免 WebDAV 客户端缓存旧证书状态。
</details>

<details>
  <summary><strong>5) 生成前端开发证书并校验（Windows / PowerShell）</strong></summary>

```powershell
.\scripts\ps1\gen-frontend-dev-cert.ps1
```

脚本会自动完成：
- 生成 `new_frontend/certs/frontend-dev.p12`
- 输出证书关键校验信息（别名、有效期、SHA256 指纹、SAN）
- 运行时提示手动输入 `StorePass`（不回显）

你也可以手动复核：

```powershell
keytool -list -v -keystore .\new_frontend\certs\frontend-dev.p12 -storetype PKCS12
```
</details>

<details>
  <summary><strong>6) 启动后端</strong></summary>

```powershell
.\scripts\ps1\start-backend.ps1
```
</details>

<details>
  <summary><strong>7) 启动前端</strong></summary>

```shell
cd new_frontend
pnpm install
pnpm dev
```
</details>

## 开发提示
<ul>
  <li>默认先启动依赖服务，再启动后端与前端。</li>
  <li>推荐使用项目内脚本统一执行测试与质量门禁，避免本地命令漂移。</li>
  <li>多协议联调建议对齐同一套场景：创建、读取、预览、下载、重命名、删除。</li>
</ul>

## 修订记录
| 版本 | 时间 | 说明 |
|---|---|---|
| v1.8 | 2026-05-31 | README 补充数据库与 Hikari 连接池环境变量说明，和 `.env.example` 保持一致 |
| v1.7 | 2026-05-30 | README 新增 Windows 导入后端证书到受信任根步骤，明确 HTTPS + WebDAV 本地联调前置条件 |
| v1.6 | 2026-05-30 | 新增后端交互式证书脚本（生成+校验+备份），README 切换为脚本化后端证书流程 |
| v1.5 | 2026-05-30 | README 增加前端证书生成脚本与校验步骤，前端 HTTPS 配置改为独立证书方案 |
| v1.4 | 2026-05-30 | README 增加 Windows 自签名证书与 HTTPS `.env` 配置说明，修正后端启动脚本路径 |
| v1.3 | 2026-05-29 | 增加打字机动效，扩展项目定位与能力说明，优化视觉密度 |
| v1.2 | 2026-05-29 | README 调整为 Markdown + HTML 混排，增强层次与视觉表现 |
| v1.1 | 2026-05-29 | README 结构升级，增加轻量风格化展示与可读性优化 |
| v1.0 | 2026-05-29 | 初始化 |
