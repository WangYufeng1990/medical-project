# Medical Backend — 面试复习大纲

> Spring Boot 3.4 + MySQL + Redis + Okta OAuth2 + HAPI FHIR R4
> 46 files changed, ~1300 lines added across 15 rounds

---

## 一、项目概览（30 秒电梯演讲）

"I built a HIPAA-compliant medical practice management backend with 21 CFR Part 11 audit trails, AES-256-GCM transparent encryption, FHIR R4 interoperability, and clinical decision support. 19 modules, 94 integration tests, full RBAC with Okta OAuth2."

---

## 二、模块全景

| 模块 | 路径 | 核心功能 |
|------|------|---------|
| **auth** | `module/system/service/AuthService.java` | Staff 登录/刷新/登出，Okta OAuth2 密码授予，dev-mode 本地 JWT，账户锁定(5 次失败→15min)，登录成功/失败全审计 |
| **patient-auth** | `module/patient/controller/PatientAuthController.java` | 患者独立登录，认证与医疗记录分离审计，同样的锁定策略；`PatientPortalController` 提供自助 profile 更新 (`PUT /api/v1/patient/me`)，name 字段需 staff 验证不可自助修改 |
| **patients** | `module/patient/` | 31 字段 US 医疗模型 CRUD，24/31 字段 AES 加密，PatientFormDTO→Entity→PatientVO(SSN 末 4 位) |
| **appointments** | `module/appointment/` | 预约调度，30 分钟冲突检测，US visit type(NEW_PATIENT/FOLLOW_UP/URGENT_CARE 等)，CPT 编码 |
| **prescriptions** | `module/prescription/` | 处方+药品项 CRUD，NDC/RxNorm/DEA/管制等级，CDS 集成(Drug-Drug + Drug-Allergy 检查) |
| **billing** | `module/billing/` | 保险理赔状态机(DRAFT→SUBMITTED→PENDING→PAID/DENIED→APPEALED)，CPT/ICD-10/POS 编码 |
| **messages** | `module/chat/` | 医患即时通讯，消息内容 AES 加密存储 |
| **dashboard** | `module/dashboard/` | JdbcTemplate 聚合统计(患者总数/今日预约/月度收入/处方数/收入趋势)，Redis 缓存 30min |
| **export** | `module/export/` | CSV 流式导出(StreamingResponseBody, 500/页)，电话→末4位、邮箱→j***@domain.com 脱敏 |
| **FHIR** | `module/patient/service/PatientCaseService.java` | FHIR R4 Bundle(Patient+Condition+AllergyIntolerance+Encounter+MedicationRequest)，US Core OMB 种族/民族扩展 |
| **FHIR REST** | `FhirPatientController`, `FhirObservationController` | GET /Patient/{id}, GET /Patient?_id=, GET /Observation/{id}?patient=&code=, SSN 脱敏，SMART on FHIR |
| **CDS** | `module/prescription/service/CdsService.java` | Drug-Drug Interaction(两两交叉检查)，Drug-Allergy Contraindication(过敏类别匹配)，CDS Hooks 端点 |
| **integration** | `module/integration/` | Mirth Connect→JSON ADT 事件(A01/A03/A08, Patient upsert)+Lab Results(sourceMessageId 去重) |
| **LOINC** | `module/patient/` | loinc_catalog 29 个常见编码(CBC/BMP/Lipid/HbA1c/TSH/UA)，autoFlag(LL/L/N/H/HH 五级) |
| **ePrescribing** | `module/prescription/service/` | NCPDP SCRIPT 10.6 NewRx XML 生成，EPCS 管制药品审计，药房目录(NPI+EPCS 支持) |
| **eCQM** | `module/quality/` | CMS122(HbA1c)/CMS125(乳腺癌筛查)/CMS165(高血压控制) SQL 性能率计算 |
| **consent** | `module/patient/controller/ConsentController.java` | HIPAA §164.508 知情同意 CRUD+revoke，患者自服务查看 |
| **emergency** | `module/system/controller/EmergencyAccessController.java` | Break-glass 紧急访问，30 分钟自动过期，同步审计 |
| **audit** | `common/audit/` | AOP @Auditable, REQUIRES_NEW 异步事务, SHA-256 row_hash 防篡改, API 查询 |

---

## 三、核心亮点

### 1. 加密体系

```
AES-256-GCM 透明加解密
├── JPA @Convert(AesAttributeConverter) — 业务层完全无感
├── PBKDF2-HMAC-SHA256 310k 迭代密钥派生 (NIST SP 800-132)
├── 版本化密文 [version:1B][IV:12B][ciphertext+tag] → 支持密钥轮换
├── LocalDateAttributeConverter — dateOfBirth 加密存储
├── PhiMaskingRedisSerializer — Redis 缓存自动检测 @PhiField → 替换为 [PHI-REDACTED]
├── 解密失败降级: return "[DECRYPT_FAILED]" — 不中断整个 JPA 查询
└── 加密字段: Patient 24/31, SysUser 3 字段, Message content, Bill insuranceClaimNumber, Prescription deaNumber
```

**面试怎么说：** "I implemented AES-256-GCM with versioned ciphertext for key rotation. Encryption is transparent to business logic via JPA AttributeConverter. The key derivation uses PBKDF2-HMAC-SHA256 with 310K iterations per NIST SP 800-132. For defense in depth, Redis cache serialization automatically redacts @PhiField-annotated fields."

### 2. 审计合规 (21 CFR Part 11)

```
审计架构:
  Business Thread              auditExecutor Thread Pool
  @Transactional               @Transactional(REQUIRES_NEW, timeout=3s)
  PatientService.create() ──→  AuditLogWriter.writeAsync()
      │                              │
      ▼                              ▼
  AuditLogAspect.audit()       auditLogRepository.save()
  (capture userId/username/      ├── SHA-256 row_hash
   patientId/IP/targetId)         ├── archived flag (never DELETE)
                                  └── @SQLRestriction("archived=0")
```

**防篡改措施：**
- SHA-256 row_hash 列 — 每行内容哈希
- @SQLRestriction + archived=1 软删除 — 不物理删除
- DataRetentionJob 改为归档(archived=1)而非 deleteByCreateTimeBefore()
- 登录成功/失败全追踪(LOGIN_SUCCESS + 5 种失败原因)
- 角色/菜单 CRUD 全审计

**面试怎么说：** "The audit trail is 21 CFR Part 11 compliant: every row has a SHA-256 hash for tamper detection, audit records are soft-deleted rather than physically removed, login successes and failures are both traced with reason codes, and role/menu permission changes are all audited."

### 3. FHIR R4 互操作

```
FHIR 端点:
  GET /api/v1/fhir/metadata           → CapabilityStatement (FHIR 4.0.1 + SMART on FHIR + OAuth2 URIs)
  GET /api/v1/fhir/Patient/{id}       → FHIR Patient resource (SSN masked)
  GET /api/v1/fhir/Patient?_id=       → FHIR Bundle (search)
  GET /api/v1/fhir/Observation/{id}   → FHIR Observation + abnormal flag interpretation Coding
  GET /api/v1/fhir/Observation?patient=&code= → trend Bundle
  GET /api/v1/patients/{id}/case      → Comprehensive Bundle (Patient+Condition+AllergyIntolerance+Encounter+MedicationRequest)

编码系统:
  SSN: http://hl7.org/fhir/sid/us-ssn
  MRN: http://hl7.org/fhir/sid/us-mrn
  NDC: http://hl7.org/fhir/sid/ndc
  RxNorm: http://www.nlm.nih.gov/research/umls/rxnorm
  US Core Race: ombCategory Coding (OMB 2106-3/2054-5/2028-9/1002-5/2076-8)
  US Core Ethnicity: ombCategory Coding (2135-2/2186-5)
  SMART: http://hl7.org/fhir/smart-app-launch
```

**面试怎么说：** "I built FHIR R4 Patient and Observation RESTful endpoints using HAPI FHIR 7.4. The CapabilityStatement advertises SMART on FHIR security with OAuth2 URIs. Race and ethnicity extensions use proper US Core ombCategory Codings with OMB system URIs, not plain strings."

### 4. 临床决策支持 (CDS)

```
处方便创建时的 CDS 检查流程:
  PrescriptionService.create()
    ├── 1. 保存处方和药品项
    ├── 2. CdsService.checkDrugInteractions()
    │       └── 两两交叉 drug_interaction 表
    │           └── 返回 severity: contraindicated/severe/moderate/minor
    ├── 3. CdsService.checkAllergyContraindications()
    │       └── 患者 allergies × drug_allergy_class 表
    │           └── 匹配 → contraindicated
    └── 4. 警告记入日志 → 不阻断处方

独立检查端点:
  POST /api/v1/cds/check  ← 处方前预筛查
```

**面试怎么说：** "When a prescription is created, the CDS engine automatically scans for drug-drug interactions and drug-allergy contraindications. The interaction rules table stores severity levels and clinical recommendations. Warnings are logged but don't block prescribing—the doctor can override with reason documentation."

### 5. 账户安全

```
系统用户 (AuthService) + 患者 (PatientAuthController):
  密码策略: @ValidPassword → 8+ chars, upper/lower/digit/special
  密码历史: password_history 表 → 最近 3 次不可重用
  账户锁定: 5 次失败 → 15 分钟锁定 (原子 JPQL @Modifying UPDATE)
  密码派生: PBKDF2-HMAC-SHA256 310k 迭代
  Token 外置: app.security.access-token-expiry-seconds (默认 7200s)

登录安全:
  速率限制: 10 req/min/IP (login), 20 req/min/IP (refresh)
  Dev-mode 显式声明: app.security.dev-mode=true (不能自动降级)
  安全响应头: HSTS(1yr), X-Content-Type-Options, X-Frame-Options:DENY, XSS, Cache-Control
```

**面试怎么说：** "I implemented atomic account lockout using @Modifying JPQL queries to prevent race conditions. The dev-mode authentication requires an explicit flag—missing Okta config in production causes a hard failure, not a silent downgrade to local JWT."

### 6. 反 DoS

| 措施 | 位置 |
|------|------|
| FHIR 搜索分页 (_count/_offset, max=500) | FhirPatientController, FhirObservationController |
| CSV 流式导出 (StreamingResponseBody, 500/页) | ExportController |
| 紧急访问历史分页 (max 500) | EmergencyAccessController |
| 登录/刷新/导出三级限速 | RateLimiterConfig |
| RestTemplate 池化 (5s 连接/10s 读取超时) | RestTemplateConfig |

---

## 四、数据库表 (27 张)

```
业务表: sys_user, sys_role, sys_menu, sys_user_role, sys_role_menu
医疗表: patient, appointment, prescription, prescription_item, bill
通信表: message, patient_auth
审计表: audit_log, password_history, consent, emergency_access, key_audit
CDS 表: drug_interaction, drug_allergy_class, cds_override
检验表: observation, loinc_catalog
药房表: pharmacy_directory
质量表: quality_measure
```

---

## 五、技术栈清单

| 层 | 技术 | 版本 |
|----|------|------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.4.1 |
| Security | Spring Security + OAuth2 Resource Server | 6.x |
| ORM | Spring Data JPA + Querydsl | Hibernate 6.x |
| DB | MySQL | 8.0+ |
| Cache | Redis + Redisson | 7.x / 3.x |
| FHIR | HAPI FHIR R4 | 7.4 |
| API Doc | Springdoc OpenAPI | 2.7.0 |
| Build | Maven | 4.x |
| Testing | JUnit 5 + Spring Boot Test | 94 集成测试 |

---

## 六、常见面试追问

**Q: 为什么用 AES-GCM 而不是 AES-CBC？**
A: GCM 是 AEAD (Authenticated Encryption with Associated Data)，同时提供机密性和完整性。CBC 只提供机密性，需要单独的 HMAC。医疗数据需要防篡改——攻击者修改密文中的 lab 结果可能导致误诊。GCM 的认证标签 (128-bit) 通过 Cipher.doFinal() 自动验证。

**Q: 密钥轮换是怎么实现的？**
A: 密文前缀 1 字节版本号。v1 用 app.aes.key 加密，v0 用 app.aes.key.previous 解密。轮换流程：更新 app.aes.key → 旧 key 设为 previous → 业务自然写入逐步重加密 → 确认无旧数据后移除 previous。key_audit 表记录每次 INIT/ROTATION 事件。

**Q: 审计日志怎么防止管理员篡改？**
A: 每行 SHA-256 row_hash=hash(userId|username|patientId|module|action|targetId|detail|ip|createTime)。表使用 @SQLRestriction("archived=0") + DataRetentionJob 只做 archived=1 软删除，不物理删除。生产环境建议 MySQL TRIGGER 阻止 UPDATE，但代码层已做了哈希链。

**Q: FHIR 怎么处理 SSN？**
A: 存储时 AES 加密，FHIR 输出时掩码为 ***-**-6789（只显示后 4 位），满足 HIPAA 最小必要标准。

**Q: CDS 检查怎么保证不误杀处方？**
A: 只做 WARNING（不阻断），医生可以 override 并在 cds_override 表记录理由。这是符合临床实践的——有些药物组合在特定情况下是必要的。

**Q: 为什么患者认证要分离？**
A: HIPAA 要求认证记录和医疗记录分开审计。Patient 表存医疗数据，PatientAuth 表存凭证+锁定状态，通过 patientId FK 关联。这样医疗数据查询不会触发认证表锁，审计时也能区分是"谁登录了"还是"谁查看了数据"。

**Q: 患者能自助修改自己的姓名吗？**
A: 不能。`PUT /api/v1/patient/me` 允许患者修改 phone/email/address/emergencyContact，但后端显式忽略 `name` 字段。HIPAA §164.526 赋予患者请求修改 PHI 的权利，但 legal name 变更需要出示身份证件由 staff 验证后操作——这和 Epic MyChart 等主流 EHR 的做法一致。如果需要，可以通过 `PUT /api/v1/patients/{id}` (ADMIN/DOCTOR) 修改。
