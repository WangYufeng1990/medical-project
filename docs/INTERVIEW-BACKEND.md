# Medical Backend — 面试复习大纲（中文版）

> Spring Boot 3.4 + MySQL + Redis + Okta OAuth2 + HAPI FHIR R4
> 19 模块 | 27 张表 | 120 集成测试 | 15 Rounds 演进

---

## 目录

1. [30 秒电梯演讲](#一30-秒电梯演讲)
2. [19 模块全景](#二19-模块全景)
3. [6 大核心亮点](#三6-大核心亮点)
4. [关键技术决策与权衡](#四关键技术决策与权衡)
5. [数据库设计 (27 张表)](#五数据库设计-27-张表)
6. [安全架构分层](#六安全架构分层)
7. [API 端点统计](#七api-端点统计)
8. [技术栈清单](#八技术栈清单)
9. [常见面试追问 (15 题)](#九常见面试追问-15-题)
10. [行为面试要点](#十行为面试要点)

---

## 一、30 秒电梯演讲

> "I built a HIPAA-compliant medical practice management system from scratch — Spring Boot backend with 21 CFR Part 11 audit trails, AES-256-GCM transparent encryption with versioned key rotation, FHIR R4 interoperability using HAPI FHIR, and clinical decision support for drug-drug interaction and drug-allergy checking. 19 modules, 27 database tables, 120 integration tests, full RBAC with Okta OAuth2."

---

## 二、19 模块全景

### 2.1 核心业务模块 (6 个)

| 模块 | 路径 | 核心功能 | 关键技术点 |
|------|------|---------|-----------|
| **patients** | `module/patient/` | 31 字段美国医疗模型 CRUD | 24/31 字段 AES 加密；PatientFormDTO→Entity→PatientVO(SSN 末4位) |
| **appointments** | `module/appointment/` | 预约调度+冲突检测 | 30分钟冲突窗口；US visit type(5 种)；CPT E&M 编码 |
| **prescriptions** | `module/prescription/` | 处方+药品项 CRUD | NDC/RxNorm 双编码；DEA 管制等级；CDS 集成 |
| **billing** | `module/billing/` | 保险理赔状态机 | DRAFT→SUBMITTED→PENDING→PAID/DENIED→APPEALED 6 状态 |
| **chat** | `module/chat/` | 医患即时通讯 | 消息内容 AES 加密；双向对话查询 |
| **dashboard** | `module/dashboard/` | 聚合统计面板 | JdbcTemplate 直接 SQL；Redis 缓存 30min |

### 2.2 认证与权限模块 (3 个)

| 模块 | 路径 | 核心功能 | 关键技术点 |
|------|------|---------|-----------|
| **auth** | `module/system/service/AuthService.java` | Staff 登录/刷新/登出 | Okta OAuth2 password grant；dev-mode 显式声明（不能自动降级）；登录成功/失败全审计（5 种失败原因码）|
| **patient-auth** | `module/patient/controller/PatientAuthController.java` | 患者独立登录 | 认证与医疗记录分离（PatientAuth 表独立）；5 次失败→15 分钟原子锁定 |
| **user-profile** | `module/system/controller/UserProfileController.java` | 个人信息管理 | 密码复杂度校验（@ValidPassword）+ 历史禁止重用（password_history 表）|

### 2.3 FHIR 互操作模块 (2 个)

| 模块 | 路径 | 核心功能 | 关键技术点 |
|------|------|---------|-----------|
| **FHIR Case** | `PatientCaseService.java` | 患者完整病历 Bundle | Patient+Condition+AllergyIntolerance+Encounter+MedicationRequest 五种资源；US Core OMB Coding 种族/民族扩展 |
| **FHIR REST** | `FhirPatientController`, `FhirObservationController` | 资源级 FHIR 端点 | `GET /Patient/{id}` (SSN 掩码)；`GET /Observation?patient=&code=`；SMART on FHIR CapabilityStatement |

### 2.4 临床决策支持 (1 个)

| 模块 | 路径 | 核心功能 | 关键技术点 |
|------|------|---------|-----------|
| **CDS** | `module/prescription/service/CdsService.java` | 药物交互+过敏禁忌检查 | Drug-Drug Interaction（两两交叉 drug_interaction 表）；Drug-Allergy Contraindication（患者 allergies × drug_allergy_class 表）；不阻断处方，医生可 override |

### 2.5 互操作与检验模块 (3 个)

| 模块 | 路径 | 核心功能 | 关键技术点 |
|------|------|---------|-----------|
| **integration** | `module/integration/` | Mirth Connect JSON API | ADT 事件（A01/A03/A08）→ Patient upsert by MRN；Lab Results → 批量写入 observation 表 + sourceMessageId 去重 |
| **LOINC** | `module/patient/` | 检验编码知识库 | loinc_catalog 29 个常用编码（CBC/BMP/Lipid/HbA1c/TSH/UA）；autoFlag LL/L/N/H/HH 五级异常标识 |
| **ePrescribing** | `module/prescription/service/` | 电子处方 | NCPDP SCRIPT 10.6 NewRx XML 生成；EPCS 管制药品双因素审计；药房目录(NPI+EPCS 支持) |

### 2.6 合规与审计模块 (4 个)

| 模块 | 路径 | 核心功能 | 关键技术点 |
|------|------|---------|-----------|
| **audit** | `common/audit/` | 业务操作审计 | AOP @Auditable；REQUIRES_NEW 异步事务；SHA-256 row_hash 防篡改；ADMIN 查询 API |
| **consent** | `module/patient/controller/ConsentController.java` | HIPAA §164.508 知情同意 | CRUD+revoke；患者自服务查看 |
| **emergency** | `module/system/controller/EmergencyAccessController.java` | Break-glass 紧急访问 | 30 分钟自动过期；同步审计（audited=1）；WARN 级别日志 |
| **key-audit** | `common/audit/KeyAudit.java` | 密钥生命周期审计 | KEY_INIT/KEY_ROTATION 事件记录；ADMIN 历史查询 |

### 2.7 其他模块 (3 个)

| 模块 | 路径 | 核心功能 | 关键技术点 |
|------|------|---------|-----------|
| **export** | `module/export/` | CSV 数据导出 | StreamingResponseBody 流式写入（500/页）；PHI 自动掩码（电话→末4位，邮箱→j***@domain.com）|
| **eCQM** | `module/quality/` | CMS 临床质量度量 | CMS122(HbA1c)/CMS125(乳腺癌筛查)/CMS165(高血压控制)；SQL 查询+性能率计算 |
| **data-retention** | `common/job/DataRetentionJob.java` | 数据留存管理 | 定时任务（@Scheduled cron）；审计日志 6 年存档（archived 软删除）|

---

## 三、6 大核心亮点

### 3.1 加密体系

**完整技术栈：**
```
┌─────────────────────────────────────────────────┐
│ 密钥派生: PBKDF2-HMAC-SHA256, 310,000 迭代        │
│         + "medical-aes-v2-salt" 固定盐            │
│         NIST SP 800-132 合规                      │
├─────────────────────────────────────────────────┤
│ 加密算法: AES-256-GCM / NoPadding                 │
│         GCM = AEAD (Authenticated Encryption)     │
│         = 机密性 + 完整性 + 防篡改                │
├─────────────────────────────────────────────────┤
│ 密文格式: [version:1B][IV:12B][ciphertext+N][tag:16B] │
│         → hex 编码存储                            │
│         version=0x01 当前密钥, 无前缀=旧数据       │
├─────────────────────────────────────────────────┤
│ JPA 透明层: @Convert(converter=AesAttributeConverter) │
│          业务代码读写普通 String，完全无感          │
│          Static bridge 解决 JPA/Spring DI 冲突     │
├─────────────────────────────────────────────────┤
│ 特殊类型: LocalDateAttributeConverter             │
│         dateOfBirth 加密存储为 VARCHAR(100)        │
├─────────────────────────────────────────────────┤
│ 解密降级: return "[DECRYPT_FAILED]"               │
│         不抛异常，不中断整个 JPA 查询              │
├─────────────────────────────────────────────────┤
│ 缓存防护: PhiMaskingRedisSerializer               │
│         序列化时自动检测 @PhiField → 替换为        │
│         "[PHI-REDACTED]"                          │
└─────────────────────────────────────────────────┘
```

**加密字段清单（24/31 Patient 字段 + SysUser + Message + Bill + Prescription）：**

| 实体 | 加密字段 |
|------|---------|
| Patient | name, ssn, phoneMobile, phoneHome, phoneWork, email, addressLine1, addressLine2, city, state, zipCode, emergencyContactName, emergencyContactPhone, insurancePayer, insuranceMemberId, insuranceGroupNumber, primaryCareProvider, medicalHistory, allergies, dateOfBirth |
| SysUser | phone, stateLicenseNumber, deaNumber, email |
| Message | content |
| Bill | insuranceClaimNumber |
| Prescription | deaNumber |

**密钥轮换流程：**
```
Step 1: 新密钥 → app.aes.key，旧密钥 → app.aes.key.previous
Step 2: 新加密操作自动使用 v1 前缀 + current key
Step 3: 解密时检测版本字节：v1→current key，无前缀→previous key（回退 current key）
Step 4: 业务自然写入过程中，旧数据逐步重新加密为 v1
Step 5: 确认无旧数据后，移除 app.aes.key.previous
Step 6: key_audit 表自动记录 KEY_ROTATION 事件
```

**面试怎么说：**
"I implemented AES-256-GCM with versioned ciphertext for key rotation — each encrypted value is prefixed with a 1-byte version identifier. Encryption is transparent via JPA @Convert. The key derivation uses PBKDF2-HMAC-SHA256 with 310,000 iterations per NIST SP 800-132. For defense in depth, Redis cache serialization automatically redacts @PhiField-annotated DTO fields to [PHI-REDACTED]. Decryption failures return a placeholder rather than throwing — so one corrupted row doesn't crash the entire JPA query."

### 3.2 审计合规 (21 CFR Part 11)

**审计架构：**
```
Business Thread                      auditExecutor Thread Pool (core=2, max=4, queue=500)
─────────────                        ───────────────────────────────────────
@Transactional                       @Transactional(propagation=REQUIRES_NEW, timeout=3s)
PatientService.create() ──commit→    AuditLogWriter.writeAsync()
    │                                       │
    ├── AuditLogAspect.around()             ├── AuditLog entity
    │   ├── capture userId                  │   ├── SHA-256 row_hash (防篡改)
    │   ├── username (SecurityContext)      │   ├── archived=0 (默认)
    │   ├── patientId (从参数解析)          │   ├── @PrePersist → computeRowHash()
    │   ├── targetId (解析 id/patientId)    │   └── auditLogRepository.save()
    │   ├── detail (phiAccess=true → [PHI]) │
    │   └── IP (HttpServletRequest)         ├── 失败 → log.error (不影响业务)
    │                                       └── 拒绝策略: CallerRunsPolicy (不丢日志)
    └── return result
```

**21 CFR Part 11 合规对照：**

| 要求 | 实现 |
|------|------|
| §11.10(e) 审计追踪的准确性和完整性 | SHA-256 row_hash = hash(userId\|username\|patientId\|module\|action\|targetId\|detail\|ip\|createTime) |
| §11.10(g) 防止记录被篡改 | @SQLRestriction("archived=0") + DataRetentionJob 只做 archived=1 软删除 |
| §11.300(b) 顺序审计追踪 | @PrePersist 自动时间戳；audit_log 表按 create_time 索引 |
| §11.200 电子签名 | EPCS 管制药品传输审计 + TODO 中的双因素验证架构 |

**审计覆盖范围：**
- 所有 CRUD 操作（Patient, Appointment, Prescription, Bill, SysUser, SysRole, SysMenu）
- 导出操作（CSV export patients/bills）
- 登录操作（LOGIN_SUCCESS + 5 种失败原因：USER_NOT_FOUND, ACCOUNT_DISABLED, ACCOUNT_LOCKED, BAD_CREDENTIALS, OKTA_AUTH_FAILED）
- 患者自助操作（self-service export, profile update）
- 紧急访问（EmergencyAccess: audited=1）

**面试怎么说：**
"The audit trail is 21 CFR Part 11 compliant. Every row is hashed with SHA-256 for tamper detection — the hash covers all data columns. Audit records are soft-deleted via an archived flag rather than physically removed. Login successes and failures are both audited with reason codes. The audit writer runs on a dedicated thread pool with REQUIRES_NEW transactions, so audit failures never roll back business transactions."

### 3.3 FHIR R4 互操作

**端点矩阵：**

| HTTP Method | Path | 返回类型 | 说明 |
|-------------|------|---------|------|
| GET | `/fhir/metadata` | CapabilityStatement | FHIR 4.0.1 + SMART on FHIR security + OAuth2 URIs |
| GET | `/fhir/Patient/{id}` | Patient | SSN 掩码为 ***-**-6789 |
| GET | `/fhir/Patient?_id=` | Bundle | 分页搜索 (_count=50, max=500) |
| GET | `/fhir/Observation/{id}` | Observation | 异常标识 Coding (H/L/N) |
| GET | `/fhir/Observation?patient=&code=` | Bundle | 趋势查询，_count=100 |
| GET | `/patients/{id}/case` | Bundle | 完整病历：Patient + Condition + AllergyIntolerance + Encounter[] + MedicationRequest[] |

**编码系统映射：**

| 编码系统 URI | 用途 | OMB 代码示例 |
|-------------|------|-------------|
| `http://hl7.org/fhir/sid/us-ssn` | SSN 标识符 | — (掩码) |
| `http://hl7.org/fhir/sid/us-mrn` | MRN 标识符 | — |
| `http://hl7.org/fhir/sid/ndc` | FDA National Drug Code | — |
| `http://www.nlm.nih.gov/research/umls/rxnorm` | RxNorm 概念编码 | — |
| `http://loinc.org` | LOINC 检验编码 | 2345-7 (Glucose), 4548-4 (HbA1c) |
| `urn:oid:2.16.840.1.113883.6.238` | OMB 种族/民族分类 | 2106-3 (White), 2054-5 (Black), 2028-9 (Asian) |
| `http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation` | 异常标识 | H (High), L (Low), N (Normal) |
| `http://hl7.org/fhir/smart-app-launch` | SMART on FHIR | — |

**US Core 扩展修正：**
- **修复前：** 种族/民族直接用 `new StringType("White")` — 不符合 US Core 规范
- **修复后：** 使用结构化 `ombCategory` Coding + `text` 扩展
  ```json
  {
    "url": "us-core-race",
    "extension": [
      { "url": "ombCategory", "valueCoding": { "system": "urn:oid:2.16.840.1.113883.6.238", "code": "2106-3", "display": "White" }},
      { "url": "text", "valueString": "White" }
    ]
  }
  ```

**面试怎么说：**
"I built FHIR R4 Patient and Observation RESTful endpoints using HAPI FHIR 7.4. The CapabilityStatement advertises SMART on FHIR with OAuth2 security URIs. Race and ethnicity extensions use proper US Core ombCategory Codings with OMB system URIs — not plain string types. SSN is masked to last-4 in all FHIR outputs per HIPAA minimum necessary."

### 3.4 临床决策支持 (CDS)

**交互检查流程：**
```
POST /api/v1/cds/check 或 PrescriptionService.create()
  │
  ├── 1. 保存处方和药品项
  │
  ├── 2. CdsService.checkDrugInteractions()
  │     └── for each pair (item[i], item[j]):
  │           drug_interaction 表查找 (双向: drug_a + drug_b OR drug_b + drug_a)
  │           → severity: contraindicated / severe / moderate / minor
  │           → description: 临床机制说明
  │           → recommendation: 临床建议
  │
  ├── 3. CdsService.checkAllergyContraindications()
  │     └── 患者 allergies (自由文本) × 处方药品 × drug_allergy_class 表
  │           → 过敏类别匹配 (如 Penicillin → Amoxicillin)
  │           → 返回 contraindicated
  │
  └── 4. 结果处理
        ├── 有警告 → log.warn → 返回 CdsWarning[] (不阻断! 医生可 override)
        └── 无警告 → passed=true
```

**种子数据示例：**
```
Metformin(6809) + Ibuprofen(5640) = moderate
  "NSAIDs may reduce antihyperglycemic effect and increase risk of lactic acidosis"
  → "Monitor blood glucose closely. Consider acetaminophen."

Amoxicillin(308191) → Penicillin allergy class
  "Patient has known allergy to Penicillin. Amoxicillin is contraindicated."
  → "Consider alternative medication. Document override reason if essential."
```

**面试怎么说：**
"When a prescription is created, the CDS engine automatically runs pairwise drug-drug interaction checks and drug-allergy contraindication screening. The interaction rules table is bidirectional and stores severity, mechanism, and clinical recommendations. Importantly, CDS warnings are advisory only — they don't block the prescription. The doctor can override with documented reasoning, which is the clinically appropriate approach."

### 3.5 账户安全体系

**四层防护：**

| 层 | 机制 | 实现细节 |
|----|------|---------|
| **密码策略** | @ValidPassword | 8+ chars, upper/lower/digit/special；password_history 表最近 3 次不可重用 |
| **锁定策略** | 原子 JPQL | 5 次失败 → 15 分钟锁定；`@Modifying UPDATE SET failed_attempts = COALESCE(failed_attempts,0)+1` 无竞态条件 |
| **认证策略** | dev-mode 显式 | `app.security.dev-mode=true` 必须显式声明；Okta 配置缺失→硬失败（不静默降级）|
| **速率限制** | Redisson RRateLimiter | login:10/min, refresh:20/min, export:5/hour |

**Dev-mode 安全演进：**
```
修复前: isDevMode = isBlank(clientId) || isBlank(issuerUri)
        → 生产环境漏配 Okta → 自动降级为本地 JWT = 安全漏洞

修复后: @Value("${app.security.dev-mode:false}") private boolean devMode;
        → 必须显式 app.security.dev-mode=true
        → @Profile({"dev","h2"}) 限制 SecurityConfigDev 作用域
        → 生产环境漏配 → 启动时硬失败
```

**面试怎么说：**
"I implemented atomic account lockout using `@Modifying` JPQL queries to eliminate the read-increment-save race condition. The dev-mode authentication requires an explicit `app.security.dev-mode=true` flag — if Okta config is missing in production, the system fails hard rather than silently downgrading to a local JWT. SecurityConfigDev is scoped to dev and h2 profiles only."

### 3.6 反 DoS 与性能

| 措施 | 实现位置 | 效果 |
|------|---------|------|
| FHIR 搜索分页 | `_count` (max=500) + `_offset` | 50k patients → 一次性最多返回 500 条 |
| CSV 流式导出 | `StreamingResponseBody` + 500/页 | 10k+ patients → 逐页输出，不构建内存 String |
| 紧急访问历史 | `PageRequest.of(0, 500)` | 防止无限增长 |
| 三级限速 | `RateLimiterConfig` (Redisson) | login 10/min, refresh 20/min, export 5/hour |
| RestTemplate 池化 | `SimpleClientHttpRequestFactory` | 连接超时 5s，读取超时 10s |
| 审计线程池 | `auditExecutor` (core=2, max=4, queue=500) | `CallerRunsPolicy` 拒绝策略 → 队列满时同步执行，不丢日志 |
| HikariCP 连接池 | 默认配置 (max=10) | 审计 `REQUIRES_NEW` 需要额外连接 |

---

## 四、关键技术决策与权衡

| 决策 | 选择 | 原因 | 权衡 |
|------|------|------|------|
| AES mode | GCM vs CBC | GCM = AEAD (机密性+完整性) | GCM IV 不能重用 (用 SecureRandom 生成 12B IV) |
| 密钥派生 | PBKDF2 310k vs SHA-256 | NIST SP 800-132 推荐 | 启动时多花 ~50ms 派生密钥 |
| 加密层 | JPA @Convert vs Service 层 | 业务代码完全无感 | Static bridge 模式增加复杂度 |
| 审计隔离 | REQUIRES_NEW vs REQUIRED | 审计失败不回滚业务 | 需要额外的数据库连接 |
| 审计删除 | archived 软删除 vs DELETE | 21 CFR Part 11 防篡改 | 表增长需要定期归档 |
| CDS 阻断 | WARNING vs BLOCK | 符合临床实践 | 需要 override 审计机制 |
| 患者认证 | 独立表 vs 同表 | HIPAA 审计分离 | 多一次 JOIN 查询 |
| 前端框架 | React vs Vue | 美国医疗市场 React >70% | Vue 在国内更流行 |
| JWT 存储 | localStorage vs HttpOnly cookie | SPA 架构简单 | XSS 风险（React 默认转义缓解）|
| API 文档 | Springdoc vs Knife4j | Spring Boot 3.4 兼容性 | Knife4j 4.5 不兼容 Spring 6.2 |
| dev 认证 | 显式 flag vs 自动检测 | 防止生产降级 | 开发和 CI 配置多一行 |

---

## 五、数据库设计 (27 张表)

**ER 关系图（简化）：**
```
sys_user ──< sys_user_role >── sys_role ──< sys_role_menu >── sys_menu
   │
patient ──< patient_auth (1:1, 认证分离)
   │
   ├──< appointment (patient_id, doctor_id → sys_user)
   ├──< prescription (patient_id, doctor_id → sys_user)
   │      └──< prescription_item
   ├──< bill (patient_id)
   ├──< message (sender_id, receiver_id)
   ├──< observation (patient_id)
   └──< consent (patient_id)

独立审计表: audit_log, password_history, emergency_access, key_audit
CDS 规则表: drug_interaction, drug_allergy_class, cds_override
检验字典: loinc_catalog
药房目录: pharmacy_directory
质量度量: quality_measure
```

**所有表名：**
```
业务: sys_user, sys_role, sys_menu, sys_user_role, sys_role_menu
医疗: patient, patient_auth, appointment, prescription, prescription_item, bill
通信: message
审计: audit_log, password_history, consent, emergency_access, key_audit
CDS: drug_interaction, drug_allergy_class, cds_override
检验: observation, loinc_catalog
药房: pharmacy_directory
质量: quality_measure
```

---

## 六、安全架构分层

```
Layer 1: 传输安全
  ├── TLS (MySQL SSL: useSSL=true&requireSSL=true)
  ├── HSTS (max-age=31536000, includeSubDomains)
  └── CORS (白名单域名)

Layer 2: 认证
  ├── Okta OAuth2 Resource Server (生产)
  ├── DevJwtEncoder (dev/h2 本地)
  └── Dev-mode 显式 flag (不能自动降级)

Layer 3: 授权
  ├── RBAC: ADMIN / DOCTOR / PATIENT
  ├── @PreAuthorize 方法级
  ├── 前端 JWT 角色解析 → 侧边栏过滤
  └── AdminGuard 路由守卫

Layer 4: 数据加密
  ├── AES-256-GCM 落盘加密 (JPA @Convert)
  ├── PBKDF2 密钥派生
  ├── 版本化密文密钥轮换
  └── Redis @PhiField 缓存脱敏

Layer 5: 审计
  ├── AOP @Auditable (业务操作)
  ├── SHA-256 row_hash (防篡改)
  ├── 登录成功/失败全追踪
  └── 紧急访问同步审计

Layer 6: 抗攻击
  ├── 三级限速 (login/refresh/export)
  ├── FHIR 分页 (max=500)
  ├── CSV 流式导出
  └── RestTemplate 池化超时
```

---

## 七、API 端点统计

| 类别 | 端点数 | 示例 |
|------|--------|------|
| 认证 | 6 | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/patient/login`, `/patient/refresh` |
| 用户管理 | 8 | CRUD `/users` + `/users/me` + `/users/me/password` + `/users/me/profile` |
| 角色/菜单 | 6 | CRUD `/roles` + `/menus` |
| 患者 | 6 | CRUD `/patients` + `/patients/{id}/case` (FHIR) |
| 患者门户 | 8 | `/patient/me` (profile, appointments, prescriptions, bills, export, consent, password, self-edit) |
| 预约 | 5 | CRUD `/appointments` + 状态过滤 |
| 处方 | 5 | CRUD `/prescriptions` + `/transmit` (eRx) |
| 账单 | 7 | CRUD `/bills` + `/submit` + `/adjudicate` + `/pay` + `/deny` |
| 聊天 | 4 | Staff `/messages` + Patient `/patient/me/messages` |
| 仪表盘 | 1 | `GET /dashboard/stats` |
| 导出 | 2 | `GET /export/patients` + `/export/bills` |
| 审计 | 1 | `GET /audit-logs` (多条件过滤) |
| FHIR | 6 | metadata, Patient/{id}, Patient, Observation/{id}, Observation, case |
| CDS | 1 | `POST /cds/check` |
| 集成 | 2 | `POST /integration/adt` + `/lab-results` |
| LOINC | 3 | catalog, panel, trend |
| 药房 | 1 | `GET /pharmacies` |
| eCQM | 2 | measures list + report |
| 知情同意 | 4 | admin CRUD + patient self-service |
| 紧急访问 | 2 | access + history |
| 密钥审计 | 1 | `GET /admin/keys/history` |
| **总计** | **~80** | |

---

## 八、技术栈清单

| 层 | 技术 | 版本 | 选型理由 |
|----|------|------|---------|
| Language | Java | 17 (LTS) | 医疗行业主流 |
| Framework | Spring Boot | 3.4.1 | 生态最完整 |
| Security | Spring Security + OAuth2 | 6.x | 原生 OAuth2 Resource Server 支持 |
| ORM | Spring Data JPA + Querydsl | Hibernate 6.x | JPA 标准 + 类型安全动态查询 |
| DB | MySQL | 8.0+ | RDBMS 广泛部署 |
| Cache | Redis + Redisson | 7.x / 3.x | Redisson 提供分布式锁和限流 |
| FHIR | HAPI FHIR R4 | 7.4 | Java 生态唯一成熟的 FHIR 库 |
| API Doc | Springdoc OpenAPI | 2.7.0 | Spring Boot 3.4 兼容 |
| Validation | Jakarta Validation | — | 声明式校验 (@ValidPassword) |
| JSON | Jackson | — | Spring Boot 默认 |
| Util | Lombok, Hutool | — | 减少样板代码 |
| Build | Maven | 4.x | 依赖管理 |
| Testing | JUnit 5 + Spring Boot Test | — | 120 集成测试 |

---

## 九、常见面试追问 (15 题)

### 加密相关

**Q1: 为什么用 AES-GCM 而不是 AES-CBC？**
GCM = AEAD (Authenticated Encryption with Associated Data)，单次操作同时提供机密性和完整性。CBC 只提供机密性，需要单独的 HMAC 来保证完整性。医疗数据被篡改可能导致误诊——修改密文中的 lab 结果（如血糖值），CBC 解密后会是乱码但不会报错，GCM 的认证标签验证失败会直接抛异常。128-bit 认证标签通过 `Cipher.doFinal()` 自动验证。

**Q2: 密钥轮换是怎么实现的？**
密文前缀 1 字节版本号 (0x01 = current key)。加密时写入 `[0x01][IV][ciphertext+tag]`，解密时读取第一字节：如果是 `0x01` 用 current key，否则用 previous key 尝试（回退 current key 兼容旧数据）。轮换步骤：新 key 设为 `app.aes.key` → 旧 key 设为 `app.aes.key.previous` → 业务自然写入逐步重加密 → 确认无旧数据后移除 previous key。`key_audit` 表自动记录每次 `KEY_INIT` / `KEY_ROTATION`。

**Q3: 为什么 PBKDF2 而不是直接用 SHA-256 做密钥派生？**
SHA-256 是快速哈希，攻击者用 GPU 可以 ~10B/s 的速度暴力破解。PBKDF2 通过 310,000 次迭代 + salt 将单次猜测时间从纳秒级提升到毫秒级，暴力破解成本增加 6 个数量级。NIST SP 800-132 推荐 PBKDF2/PKCS#5 用于密码到密钥的派生。

**Q4: [DECRYPT_FAILED] 返回字符串而不抛异常，不怕数据错乱吗？**
这是刻意设计的降级策略。在密钥轮换过渡期，存在少量旧格式数据是正常状态。如果 `decrypt()` 抛异常，会导致整个 JPA 查询失败（例如患者列表完全不加载），影响远大于单个字段的 `[DECRYPT_FAILED]` 占位符。前端检测到该字符串时展示 "Data Unavailable (Compliance Protection)"。

### 审计相关

**Q5: 审计日志怎么防止管理员篡改？**
多层防护：① 每行 SHA-256 `row_hash` = hash(所有数据列连接)；② `@SQLRestriction("archived=0")` + `DataRetentionJob` 只做 `archived=1` 软删除；③ 生产环境建议 MySQL TRIGGER 阻止 `UPDATE`/`DELETE` 操作。如果管理员修改了某行数据，`row_hash` 会不匹配，审计脚本可检测。如果管理员做了 soft-delete，`archived=1` 的记录仍在表中，只是被 `@SQLRestriction` 过滤。

**Q6: 登录失败为什么要审计？**
21 CFR Part 11 §11.300(b) 要求对每个访问事件生成审计追踪。登录失败是安全事件——短时间内大量失败可能表示暴力破解。记录 `USER_NOT_FOUND` / `ACCOUNT_DISABLED` / `ACCOUNT_LOCKED` / `BAD_CREDENTIALS` / `OKTA_AUTH_FAILED` 五种原因码，事后可以关联分析。

**Q7: 审计日志写失败了怎么办？**
`AuditLogWriter.writeAsync()` 在独立线程池上运行 `REQUIRES_NEW` 事务。如果写失败（数据库连接池耗尽、超时），只会 `log.error`，不会影响已提交的业务事务。线程池配置了 `CallerRunsPolicy`：队列满 500 时，由主请求线程同步执行，保证不会静默丢弃日志。

### FHIR 相关

**Q8: FHIR US Core 的 race/ethnicity 扩展为什么不能用 StringType？**
US Core IG 要求 race 扩展使用 `ombCategory` Coding（OMB 编码系统 `urn:oid:2.16.840.1.113883.6.238`），而非自由文本。这是因为 CMS 要求按 OMB 5 分类（White/Black/Asian/AI.AN/NH.PI）上报质量指标（eCQM），不符合规范的扩展会导致验证失败和 CMS 报告被拒。

**Q9: CapabilityStatement 里声明了 SMART on FHIR 但 OAuth2 endpoint 是 `/api/v1/auth/login`，这不是 SMART 标准 endpoint 吧？**
是的，这是简化实现。完整的 SMART on FHIR 需要独立的 `/authorize` 和 `/token` 端点。当前 CapabilityStatement 的 `security.service` 声明了 `SMART-on-FHIR`，`oauth-uris` 扩展声明了 `token` 和 `authorize` URI，但后端尚未实现独立的 OAuth2 端点。这可以作为后续的改进方向。

### 业务相关

**Q10: CDS 检查怎么保证不误杀处方？**
只返回 WARNING，不阻断处方。医生可以 override 并在 `cds_override` 表记录理由。临床上有些药物组合是必要的（如 Warfarin + Aspirin 对于某些心脏病患者），必须由医生判断。

**Q11: 为什么患者认证要分离（PatientAuth vs Patient）？**
HIPAA 要求认证记录和医疗记录分开审计。Patient 表存医疗数据（查询密集），PatientAuth 表存凭证+锁定状态（认证时查），通过 `patientId` FK 关联。分离后医疗数据查询不会触发认证表锁，审计时也能区分"谁登录了"（PatientAuth login 审计）和"谁查看了数据"（PatientService 操作审计）。

**Q12: 预约冲突检测怎么做的？**
创建/更新预约时检查同一医生在 `[appointmentTime-30min, appointmentTime+duration+30min]` 区间内是否有其他预约。使用 `JpaSpecificationExecutor` 动态构建查询条件，避免硬编码 SQL。

### 系统设计相关

**Q13: 怎么处理高并发下的账户锁定竞态条件？**
原始代码是 `read → increment → save` 三步操作，存在竞态——10 个并发失败可能导致计数器只加 2 而非 10。修复方案：使用 `@Modifying @Query("UPDATE SysUser SET failedAttempts = COALESCE(failedAttempts,0)+1 WHERE id=:id")` 原子更新，数据库行锁保证并发安全。

**Q14: 如果 Okta 宕机了怎么办？**
Okta 是现代 SaaS，SLA 通常是 99.99%。如果 Okta 真的宕机：① Staff 和患者都无法登录——这是一个系统性风险，但概率极低；② 已有的 JWT token 在过期前仍有效（默认 2 小时）；③ Emergency Access 端点仍可使用（如果已持有 token）。不应该在应用层做 Okta fallback——那会引入更大的安全风险。

**Q15: 这个系统能处理多少并发用户？**
当前架构是单体应用，适合中小型诊所（1-50 并发 providers）。对于更大规模：① 审计线程池可扩展（core/max pool size）；② Redis 缓存减少数据库压力；③ FHIR 端点已分页；④ CSV 导出流式处理。如果需要支持医院级别（500+ 并发），建议拆分读写服务、引入消息队列处理审计写入、使用读写分离数据库。

---

## 十、行为面试要点

**回答 "Tell me about yourself" 时的结构：**
1. "I'm a backend engineer with N years of experience, recently focused on healthcare compliance."
2. "I built this HIPAA-compliant medical system covering encryption, audit, FHIR interoperability, and clinical decision support."
3. "The project has 19 modules, 120 integration tests, and implements 21 CFR Part 11 audit compliance."
4. "I'm looking for a backend role where I can apply my healthcare domain knowledge."

**回答 "What was the hardest problem?" 时的选择：**
- 技术深度：AES 密钥轮换 + 版本化密文设计
- 合规难度：21 CFR Part 11 审计防篡改（哈希链 + 软删除）
- 架构决策：患者认证分离（PatientAuth vs Patient）
- 安全修复：dev-mode 显式声明（防止生产降级）

**回答 "What would you do differently?" 时：**
- "I'd start with the compliance requirements upfront (21 CFR Part 11, HIPAA) rather than retrofitting them."
- "I'd use Flyway/Liquibase for database migrations instead of raw schema.sql."
- "I'd add OpenTelemetry tracing for audit event correlation across microservices."
