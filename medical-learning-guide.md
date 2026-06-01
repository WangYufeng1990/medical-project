# 传统后端工程师 → 医疗领域：重点学习模块

> 基于 `medical-project` 代码库，面向具有传统后端经验、正在转型到美国医疗领域的工程师。

---

## 第一优先级：必须掌握（每天都在用）

### 1. PHI 加密体系

**文件：** `common/config/AesCryptoUtil.java` + `AesAttributeConverter.java`

这是医疗后端和普通后端的**最大区别**。普通后端密码加密就够了，医疗领域几乎所有个人数据都要加密。

| 项 | 说明 |
|----|------|
| 算法 | **AES-256-GCM / NoPadding**（带认证的加密，GCM 同时保证机密性和完整性） |
| IV | 每次加密随机生成 12 字节 IV |
| 认证标签 | 128-bit，防止密文被篡改 |
| 密钥派生 | `app.aes.key` → SHA-256 → 256-bit AES Key |

**透明加解密机制：**

```
业务代码读写字段（普通 String）
       ↕
JPA @Convert(converter = AesAttributeConverter.class)
       ↕
AesCryptoUtil.encrypt() / decrypt()
       ↕
数据库存储 hex 编码的 AES-GCM 密文
```

业务层完全无感——患者 `name` 在代码里就是普通字符串，JPA 层自动完成加解密。

**Static Bridge 设计（为什么需要）：**

JPA 通过 `new` 直接实例化 `AttributeConverter`，不经过 Spring 容器，所以 `@Value` 不能直接注入 Converter。

```
application.yml
  app.aes.key: ${AES_KEY:...}
       │ @Value
       ▼
AesCryptoUtil (@Component)              AesAttributeConverter (@Converter)
  @PostConstruct init()    ──static──▶   convertToDatabaseColumn()
  static encrypt(String)      bridge     └── AesCryptoUtil.encrypt()
  static decrypt(String)    ◀──static──  convertToEntityAttribute()
       │                    bridge       └── AesCryptoUtil.decrypt()
       │
       ▼
  解密失败 → log.error + return "[DECRYPT_FAILED]"
  不抛异常 — 单行坏数据不中断整个 JPA 查询
```

**降级策略：** 解密失败返回 `[DECRYPT_FAILED]` 而非抛异常。这是关键设计决策——单行数据损坏（比如密钥轮换期间的旧数据）不应导致整个查询崩溃。

**加密字段清单：**

| 实体 | 加密字段 | 原因 |
|------|---------|------|
| Patient | `name`, `ssn`, `phoneMobile`, `phoneHome`, `phoneWork`, `email`, `emergencyContactPhone`, `insuranceMemberId` | HIPAA Protected Health Information |
| SysUser | `phone`, `stateLicenseNumber`, `deaNumber` | 医护人员敏感信息 |
| Message | `content` | 聊天记录可能包含病情讨论 |
| Bill | `insuranceClaimNumber` | 保险理赔号关联患者 |
| Prescription | `deaNumber` | 管制药品开具者标识 |

**密钥轮换机制：**

密文格式 `[version:1B][IV:12B][ciphertext+tag]`。版本字节 `0x01` 表示当前密钥。无版本前缀的旧数据自动回退解密。

```
轮换步骤：
1. 更新 app.aes.key（新密钥）→ 新加密使用 v1 前缀
2. 旧密钥设为 app.aes.key.previous → 旧数据继续可解密
3. 业务自然写入 → 旧数据逐步重新加密为 v1
4. 确认无旧数据 → 移除 app.aes.key.previous
```

`AesCryptoUtil.init()` 启动时自动向 `key_audit` 表写入审计事件。

---

### 2. HIPAA 审计日志

**文件：** `common/audit/AuditLogAspect.java` + `AuditLogWriter.java` + `AuditLog.java`

HIPAA Security Rule §164.312(b) 要求记录**谁、何时、对哪个患者、做了什么操作**。

**架构：**

```
Business Thread                         auditExecutor Thread Pool
─────────────                           ────────────────────────
@Transactional                           @Transactional(REQUIRES_NEW)
PatientService.create() ─commit→         AuditLogWriter.writeAsync()
    │                                          │
    ▼                                          ▼
AuditLogAspect.audit()                  auditLogRepository.save()
  捕获: userId, username, IP,           独立事务，3s 超时
  targetId, detail                     失败 → log.error
  phiAccess? → 参数值 mask 为 [PHI]     不影响业务事务 ✓
```

**关键设计决策：**

| 决策 | 原因 |
|------|------|
| 异步写入（`@Async`） | 审计不阻塞业务响应 |
| 独立事务（`REQUIRES_NEW`） | 审计失败不回滚业务事务 |
| CallerRunsPolicy 拒绝策略 | 线程池满时同步执行，不丢日志 |
| PHI 掩码 | `phiAccess=true` → 方法参数值替换为 `[PHI]`，防止敏感数据在审计日志中二次泄露 |
| 结构化 JSON 日志（生产） | SIEM / log aggregator 友好 |
| `audit.log` 独立文件 90 天保留 | 满足审计留存要求 |

**`@Auditable` 注解参数：**

```java
@Auditable(module = "patient", action = "CREATE", phiAccess = true)
```

| 属性 | 说明 | 示例 |
|------|------|------|
| `module` | 业务模块 | patient / appointment / prescription / billing / system / export |
| `action` | 操作类型 | CREATE / UPDATE / DELETE / SUBMIT / ADJUDICATE / PAY / DENY / EXPORT |
| `phiAccess` | 是否涉及受保护健康信息 | true → 参数值替换为 `[PHI]` |

---

### 3. 美国医疗数据模型

**文件：** `module/patient/entity/Patient.java`、`module/appointment/entity/Appointment.java`、`module/prescription/entity/Prescription.java`、`module/billing/entity/Bill.java`、`module/system/entity/SysUser.java`

和国内系统字段完全不同，这是转型中**最需要重新学习的部分**。

#### 3.1 患者 (Patient)

| 美国字段 | 类型 | 说明 | 国内对应 |
|---------|------|------|---------|
| `mrn` | VARCHAR(50) UNIQUE | Medical Record Number，院内患者标识 | 病历号 |
| `ssn` | VARCHAR(200) 加密 | Social Security Number，社保号 | 身份证号 |
| `name` | VARCHAR(200) 加密 | 患者姓名（加密存储） | 姓名 |
| `dateOfBirth` | DATE | 出生日期 | 年龄（从 DOB 计算，不直接存 age） |
| `sexAtBirth` | CHAR(1) | M / F / U（生理性别） | 性别 0/1 |
| `genderIdentity` | VARCHAR(50) | 自述性别认同（Male/Female/Non-binary/Transgender...） | — |
| `race` | VARCHAR(100) | OMB 5分类（White/Black/Asian/AI.AN/NH.PI） | — |
| `ethnicity` | VARCHAR(50) | Hispanic or Latino / Not Hispanic or Latino | — |
| `preferredLanguage` | VARCHAR(10) | 首选语言（en/es/zh...） | — |
| `maritalStatus` | VARCHAR(20) | 婚姻状态（Single/Married/Divorced/Widowed） | — |
| `addressLine1/2` | VARCHAR(100) | 结构化地址 line1 + line2 | 地址（单字符串） |
| `city/state/zipCode` | VARCHAR(50)/CHAR(2)/VARCHAR(10) | 城市/州(2字母)/邮编 | — |
| `phoneMobile/Home/Work` | VARCHAR(200) 加密 | 三类电话分别存储 | 电话（单一字段） |
| `insurancePayer` | VARCHAR(100) | 保险公司名称 | 社保 |
| `insuranceMemberId` | VARCHAR(200) 加密 | 保险会员编号 | — |
| `insuranceGroupNumber` | VARCHAR(50) | 保险团体编号 | — |
| `emergencyContactName/Phone/Relation` | — | 紧急联系人 | — |
| `primaryCareProvider` | VARCHAR(100) | 初级保健医生（PCP） | — |
| `patientStatus` | VARCHAR(20) | active / inactive / deceased | — |
| `medicalHistory` | TEXT | 既往病史 | 病史 |
| `allergies` | VARCHAR(500) | 过敏史（青霉素/贝类...） | 过敏史 |

#### 3.2 医护人员 (SysUser)

| 美国字段 | 说明 |
|---------|------|
| `npi` (10位) | National Provider Identifier，全国唯一医护人员标识 |
| `deaNumber` | DEA 注册号（管制药品处方权），加密存储 |
| `stateLicenseNumber` | 州行医执照号，加密存储 |
| `licenseState` | 执照所在州（2字母，如 "IL"） |
| `taxonomyCode` | 医护人员分类码（如 "207Q00000X" = Family Medicine MD） |
| `credentials` | 资质缩写（MD/DO/NP/PA） |
| `specialty` | 专科（"Family Medicine" / "Cardiology"） |

#### 3.3 预约 (Appointment)

| 美国字段 | 值域 / 说明 |
|---------|------------|
| `visitType` | NEW_PATIENT / FOLLOW_UP / ANNUAL_PHYSICAL / URGENT_CARE / CONSULTATION |
| `chiefComplaint` | 主诉——患者就诊原因 |
| `department` | Cardiology / Family Medicine / Allergy & Immunology / ... |
| `duration` | 预约时长（分钟） |
| `cptCode` | CPT Evaluation & Management 编码（99201-99499） |
| `status` | **0=scheduled, 1=arrived, 2=cancelled, 3=completed, 4=no-show, 5=rescheduled, 6=in-progress** |
| `checkInTime / checkOutTime` | 实际签到/签出时间 |

#### 3.4 处方 (Prescription)

| 美国字段 | 说明 |
|---------|------|
| `ndcCode` | FDA National Drug Code（药品标识，如 "65862-0017-01"） |
| `rxnormCode` | NIH RxNorm 概念编码（临床药品标准化，如 "308191"） |
| `refills` | 续方次数（0 = 不可续方） |
| `daysSupply` | 发药天数 |
| `daw` | Dispense As Written（0=允许替换为仿制药, 1=必须按处方发原研药） |
| `route` | 给药途径（PO=口服 / IV=静脉 / IM=肌注 / INH=吸入 / SL=舌下） |
| `sig` | 完整服药说明（"Take one capsule three times daily with food"） |
| `frequency` | 频率（QD=每日 / BID=每日两次 / TID=每日三次 / PRN=按需） |
| `icd10Codes` | ICD-10-CM 诊断编码（如 "J06.9" = 急性上呼吸道感染） |
| `prescriberNpi` | 处方医师 NPI |
| `deaNumber` | 管制药品处方 DEA 号（加密） |
| `controlledSchedule` | 管制等级（II/III/IV/V——I 类不可处方） |
| `pharmacyName/Phone/Npi` | 发药药房信息 |
| `prescriptionType` | MEDICATION / LAB / PROCEDURE / DME（耐用医疗设备） |
| `rxStatus` | active / completed / discontinued / on-hold |

#### 3.5 账单 (Billing) — 保险理赔状态机

```
DRAFT → SUBMITTED → PENDING → PAID
                    ↘ DENIED → APPEALED
```

| 美国字段 | 说明 |
|---------|------|
| `claimStatus` | DRAFT → SUBMITTED → PENDING / PAID / DENIED |
| `totalCharge` | 总费用 |
| `insuranceAdjustment` | 保险调整额（合同折扣） |
| `insurancePayment` | 保险支付额 |
| `patientResponsibility` | 患者自付额（= total - adjustment - insurancePayment） |
| `patientPaidAmount` | 患者已付金额 |
| `copayAmount` | Copay（固定自付额） |
| `balanceDue` | 余额（= patientResponsibility - patientPaidAmount） |
| `cptCodes` | CPT 编码 |
| `icd10Codes` | ICD-10 诊断编码 |
| `placeOfServiceCode` | 服务地点编码（11=诊室, 21=住院, ...） |
| `billingProviderNpi` | 计费医师 NPI |
| `renderingProviderNpi` | 服务医师 NPI |
| `insuranceClaimNumber` | 理赔号（加密） |
| `priorAuthorizationNumber` | 预授权号（某些项目需要保险公司预批） |

**理赔生命周期方法：** `submitClaim()` → `adjudicate()` → `pay()` / `denyClaim()`

---

## 第二优先级：理解并熟悉

### 4. FHIR 互操作标准

**文件：** `module/patient/PatientCaseService.java` + `FhirConfig.java`

**背景：** 21st Century Cures Act 要求 EHR 系统支持 FHIR API（ONC 认证条件）。FHIR (Fast Healthcare Interoperability Resources) 是 HL7 制定的现代医疗数据交换标准。

**技术栈：** HAPI FHIR 7.4（`org.hl7.fhir.r4`），直接使用 FHIR 原生类型而非自定义 DTO。

**端点：**

| 端点 | 输出 | 说明 |
|------|------|------|
| `GET /api/v1/fhir/metadata` | `CapabilityStatement` (JSON) | FHIR 4.0.1 + SMART on FHIR security + OAuth2 URIs |
| `GET /api/v1/fhir/Patient/{id}` | `Patient` (JSON) | 单个 FHIR Patient 资源（SSN 掩码末4位） |
| `GET /api/v1/fhir/Patient?_id={id}` | `Bundle` (JSON) | FHIR 搜索 |
| `GET /api/v1/patients/{id}/case` | `Bundle` (JSON) | 患者完整病历 |

**SMART on FHIR：** CapabilityStatement 声明 `SMART-on-FHIR` 安全服务。JWT `scp` 包含 `patient/*.read`、`user/*.read`、`system/*.read` 等 FHIR 作用域。

**US Core Profile：** 种族/民族扩展使用结构化 `ombCategory` Coding（OMB 编码 `urn:oid:2.16.840.1.113883.6.238`）+ `text` 扩展，符合 US Core 规范。

**Bundle 包含的资源：**

```
Bundle
  ├── Patient          — 患者人口统计信息
  ├── Condition[]      — 诊断记录（基于 medicalHistory + icd10Codes）
  ├── AllergyIntolerance[] — 过敏记录
  ├── Encounter[]      — 就诊记录（基于 Appointment）
  └── MedicationRequest[] — 处方记录（基于 Prescription + Items）
```

**FHIR 编码系统：**

| 编码系统 URI | 用途 |
|-------------|------|
| `http://hl7.org/fhir/sid/us-ssn` | SSN 标识符 |
| `http://hl7.org/fhir/sid/us-mrn` | MRN 标识符 |
| `http://hl7.org/fhir/sid/ndc` | FDA National Drug Code |
| `http://www.nlm.nih.gov/research/umls/rxnorm` | RxNorm 概念编码 |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-race` | US Core Race 扩展（OMB 分类） |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity` | US Core Ethnicity 扩展 |

---

### 5. Okta OAuth2 认证 + RBAC 权限

**文件：** `security/JwtClaimMapper.java` + `common/config/SecurityConfig.java` + `SecurityConfigDev.java`

```
生产环境：                        开发环境（dev profile）：
┌─────────┐                      ┌──────────────────┐
│  Okta   │ ← JWKS endpoint      │ SecurityConfigDev │
│  签发JWT  │    (RS256公钥)        │ 本地 JwtDecoder   │
│  MFA内置  │                      │ + DevJwtEncoder  │
└────┬────┘                      │ HMAC-SHA256      │
     │                           └──────────────────┘
     ▼
┌─────────────┐
│ JwtDecoder  │ ← Spring Boot 自动从 issuer-uri 加载 JWKS
│ 验证签名+过期  │
└──────┬──────┘
       ▼
┌──────────────┐
│JwtClaimMapper│ ← Okta claims → Spring Security
│ groups→ROLE_* │   groups → ROLE_ADMIN / ROLE_DOCTOR / ROLE_PATIENT
│ scp→SCOPE_*  │   uid → userId
└──────────────┘
```

**登录流程：**

1. `POST /api/v1/auth/login` 发送 username/password
2. AuthService 本地 BCrypt 验证用户存在且未禁用
3. 生产环境 → 调用 Okta `/v1/token`（password grant）获取 access_token + refresh_token
4. 开发环境 → DevJwtEncoder 本地签发 HMAC-SHA256 JWT（无需 Okta）
5. 前端存储 token，后续请求带 `Authorization: Bearer <token>`
6. Spring Security 通过 Okta JWKS 公钥（生产）或本地 HMAC（开发）验证签名
7. JwtClaimMapper 提取 `groups` claim → `ROLE_ADMIN/DOCTOR/PATIENT`

**Token Refresh 轮换（生产环境）：**

```
POST /api/v1/auth/refresh { refreshToken }
  → Okta /v1/token (grant_type=refresh_token)
  → 旧 refresh_token 在 Okta 侧自动失效（轮换）
  → 返回新的 access_token + refresh_token
```

**RBAC 模型：** 用户 → 角色 → 菜单权限 → 接口访问。方法级 `@PreAuthorize`：

```java
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")   // 医生或管理员
@PreAuthorize("hasRole('ADMIN')")               // 仅管理员
@PreAuthorize("hasRole('PATIENT')")             // 患者门户
```

---

### 6. 软删除 + 乐观锁 + 统一基类

**文件：** `common/base/BaseEntity.java`

所有业务表继承 `BaseEntity`，自动获得：

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createTime;   // @PrePersist 自动设置
    private LocalDateTime updateTime;   // @PreUpdate 自动更新
    private Integer isDeleted;          // 0=正常, 1=已删除

    @Version
    private Integer version;            // 乐观锁
}
```

**子类实体需要的两个注解：**

```java
@Entity
@SQLDelete(sql = "UPDATE xxx SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Patient extends BaseEntity { ... }
```

| 机制 | 作用 | 为什么医疗需要 |
|------|------|--------------|
| `@SQLDelete` | `deleteById()` 只标记 `is_deleted=1`，不物理删除 | 审计追溯、数据恢复 |
| `@SQLRestriction` | 所有查询自动附加 `WHERE is_deleted=0` | 业务层无感 |
| `@Version` | 更新时检查版本号，冲突则抛异常 | 防止并发覆写（多医生同时编辑同一患者） |

---

## 第三优先级：知道即可

### 7. 患者认证分离 (PatientAuth)

**文件：** `module/patient/entity/PatientAuth.java` + `PatientAuthController.java`

HIPAA 要求认证记录和医疗记录分开审计。

```
Before:                          After:
┌─────────────────────┐          ┌─────────────────────┐
│ Patient             │          │ Patient             │
│  - username ❌      │          │  - name, mrn, ssn   │
│  - password ❌      │          │  - dateOfBirth ...  │
│  - medicalHistory   │          └─────────┬───────────┘
└─────────────────────┘                    │ patient_id (FK)
                                  ┌─────────▼───────────┐
                                  │ PatientAuth         │
                                  │  - username ✅      │
                                  │  - password (BCrypt)│
                                  │  - failedAttempts   │
                                  │  - lockedUntil      │
                                  └─────────────────────┘
```

**账户锁定：** 系统用户（AuthService）和患者（PatientAuthController）统一策略：登录失败 5 次 → 锁定 15 分钟。`SysUser` 和 `PatientAuth` 均含 `failedAttempts`、`lockedUntil` 字段。

**密码策略：** `@ValidPassword` 注解强制 8+ 位、大写、小写、数字、特殊字符。`password_history` 表禁止重用最近 3 次密码。`passwordChangedAt` 字段追踪密码修改时间。

---

### 8. 限流 + 缓存策略

**文件：** `common/config/RateLimiterConfig.java` + `CacheConfig.java`

| 端点 | 限制 | 技术 |
|------|------|------|
| `/api/v1/auth/login` | 10次/分钟/IP | Redisson RRateLimiter |
| `/api/v1/patient/login` | 10次/分钟/IP | Redisson RRateLimiter |
| `/api/v1/export/*` | 5次/小时/IP | Redisson RRateLimiter |

超限返回 HTTP 429。

**缓存策略（关键）：**

| 缓存 | 不缓存 |
|------|--------|
| Dashboard 统计数据 | 患者 ePHI（防止解密数据在 Redis 中泄露） |
| SysUser 基本信息 | — |

**Redis PHI 防护：** `PhiMaskingRedisSerializer` 序列化时自动检测 `@PhiField` 注解，将标记字段替换为 `[PHI-REDACTED]`——即使误缓存了含 PHI 的对象也不会泄露明文。

---

### 9. 结构化日志

**文件：** `resources/logback-spring.xml`

| 环境 | 格式 | 说明 |
|------|------|------|
| dev/h2 | 人类可读 | 控制台彩色输出 |
| prod | **JSON** | SIEM/log aggregator 友好（Elasticsearch / Splunk） |
| prod audit | 独立 `logs/audit.log` | 90 天保留，满足 HIPAA 审计留存 |

---

### 10. 数据导出安全

**文件：** `module/export/` + `util/CsvUtil.java`

导出患者和账单数据为 CSV。安全措施：
- `@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")` — 仅授权角色
- `@Auditable(phiAccess = true)` — 导出操作记入审计日志
- PHI 掩码：电话仅显示末4位，Email 显示 `j***@domain.com`
- 限流 5次/小时/IP — 防止批量数据泄露
- 患者自服务导出：`GET /api/v1/patient/me/export` (HIPAA 164.524 数据访问权)

### 10b. 审计日志查询

`GET /api/v1/audit-logs` (ADMIN) — 多条件过滤（userId/patientId/module/action/日期范围）+ 分页。底层 `JpaSpecificationExecutor` + 动态 `Predicate`。

### 10c. 紧急访问 (Break-Glass)

`POST /api/v1/emergency/access/{patientId}` — 医生可突破权限查看患者数据，30 分钟自动过期，同步审计。

### 10d. 知情同意管理

`consent` 表 + `POST /api/v1/consent` (ADMIN) + `GET /api/v1/patient/me/consent` (PATIENT)。支持 OPT_IN/OPT_OUT/TREATMENT/RESEARCH 类型 + revoke。

### 10e. 数据留存

`DataRetentionJob` 定时清除过期审计日志（`app.retention.audit-log-days=2190` = 6 年 HIPAA 要求）。

---

## 建议阅读顺序（按代码依赖关系）

```
 1. BaseEntity              ← 理解所有表的公共字段（id/isDeleted/version）
 2. Patient entity          ← 第一个完整业务实体，理解美式字段设计
 3. PatientService          ← CRUD + JPA Specification 动态查询
 4. PatientFormDTO / VO     ← DTO 转换模式（fromEntity / toEntity）
 5. AesCryptoUtil           ← 加密算法、密钥版本化轮换、static bridge 模式
 6. AesAttributeConverter   ← JPA Converter 如何透明加解密
 7. @ValidPassword          ← 密码复杂度校验注解
 8. AuditLogAspect          ← AOP 如何拦截 @Auditable 方法
 9. AuditLogWriter          ← 异步写入 + REQUIRES_NEW 独立事务
10. AuditLogController      ← ADMIN 审计日志查询 API
11. SecurityConfig          ← Spring Security + OAuth2 + 安全响应头
12. JwtClaimMapper          ← Okta claims → Spring Security 权限映射
13. AuthService             ← 登录 + 账户锁定 + Token 签发（含 FHIR 作用域）
14. PatientCaseService      ← FHIR Bundle 构建 + US Core 扩展（FHIR 互操作）
15. FhirPatientController   ← FHIR RESTful 端点 + SMART on FHIR
16. BillService             ← 保险理赔状态机
17. DataRetentionJob        ← 定时数据留存清除
18. EmergencyAccessController ← Break-Glass 紧急访问
19. DataInitializer         ← 种子数据覆盖所有模块
```

---

## 关键外部知识

| 知识领域 | 具体内容 | 相关模块 |
|---------|---------|---------|
| **HIPAA Security Rule** | §164.312 技术保障措施（加密/审计/访问控制） | 全部 |
| **ONC USCDI v3+** | 美国核心数据互操作标准（决定了 Patient 有哪些字段） | Patient |
| **ICD-10-CM** | 诊断编码体系（J06.9=上呼吸道感染，E11.9=2型糖尿病...） | Prescription, Billing |
| **CPT E&M Codes** | 诊疗计费编码（99213=中复杂度随访，99214=高复杂度...） | Appointment, Billing |
| **NDC / RxNorm** | FDA 药品编码 + NIH 临床药品编码（双体系） | Prescription |
| **NPI Registry** | 美国医护人员 10 位唯一标识 | SysUser |
| **DEA Controlled Substances** | 管制药品分级（Schedule II-V） | Prescription |
| **HL7 FHIR R4** | 现代医疗数据交换标准 | PatientCase, FhirConfig |
| **OAuth2 / OIDC** | Okta 认证协议 | Security |
| **SMART on FHIR** | FHIR + OAuth2 的应用启动标准 | 未来扩展方向 |
