# Medical 项目后端 — 面向美国医疗合规的架构详解

---

## 第一层：项目整体概览

这个项目是一个 **美国医疗管理系统的后端（HIPAA / ONC 合规）**。它为前端提供 RESTful API，管理患者、预约、处方、账单、即时通讯等核心医疗业务流程。

三个核心合规目标贯穿整个架构：

| 目标 | 对应标准 | 架构落地 |
|------|---------|---------|
| **数据加密** | HIPAA Security Rule §164.312 | AES-256-GCM 自动加解密敏感字段 |
| **审计追踪** | HIPAA §164.312(b) | 异步 AOP 业务级审计日志 |
| **访问控制** | HIPAA §164.312(a)(1) | Okta OAuth2 + RBAC + 方法级权限 |
| **互操作性** | 21st Century Cures Act / ONC | HAPI FHIR R4 + USCDI 扩展 |

---

## 第二层：技术选型

| 技术 | 版本 | 作用 |
|------|------|------|
| **Java** | 17 (LTS) | 运行平台 |
| **Spring Boot** | 3.4.x | 框架骨架 |
| **Spring Security + Okta OAuth2** | 3.x | 身份认证 — Okta 签发令牌，后端 JWKS 验证 |
| **Spring Data JPA + QueryDSL** | 6.x / 5.x | ORM + 类型安全动态查询 |
| **HAPI FHIR** | 7.4 | FHIR R4 原生类型（Bundle/Patient/Encounter/MedicationRequest） |
| **MySQL** | 8.0+ | 主数据库 |
| **Redis + Redisson** | 7.x / 3.x | 缓存 + 限流 |
| **Knife4j (Swagger)** | 4.5 | API 文档 |
| **Lombok / Hutool** | latest | 代码简化 + 工具集 |

**明确排除的技术：** MyBatis/MyBatis-Plus、Spring Cloud 微服务、gRPC/GraphQL、MongoDB/Elasticsearch、消息队列、Shiro、自签发 JWT。

---

## 第三层：项目目录结构

```
medical-server/src/main/java/com/example/medical/
│
├── MedicalApplication.java              ← Spring Boot 入口
│
├── common/                              ← 公共基础设施
│   ├── base/         BaseEntity（id/createTime/updateTime/isDeleted/version）
│   ├── config/       SecurityConfig, SecurityConfigDev, FhirConfig,
│   │                 AesCryptoUtil, AesAttributeConverter, AsyncConfig,
│   │                 CacheConfig, RateLimiterConfig, DataInitializer
│   ├── audit/        @Auditable, AuditLogAspect, AuditLogWriter, AuditLog
│   ├── enums/        ResultCode
│   ├── exception/    BusinessException, GlobalExceptionHandler
│   └── result/       Result<T>, PageResult<T>
│
├── security/                            ← 认证适配
│   ├── JwtUtils        (Okta token 解析)
│   ├── JwtClaimMapper  (Okta claims → Spring Security)
│   └── LoginUser       (当前登录用户封装)
│
├── util/                                ← 工具类
│   └── CsvUtil
│
└── module/                              ← 业务模块
    ├── system/      用户/角色/菜单/登录（Okta OAuth2）
    ├── patient/     患者管理 + PatientAuth 分离 + FHIR 病历
    ├── appointment/ 预约管理（US visit type + CPT code）
    ├── prescription/处方管理（NDC/RxNorm/DEA/controlled substance）
    ├── billing/     账单管理（保险理赔 lifecycle + CPT/ICD-10）
    ├── chat/        即时通讯（消息内容加密）
    ├── dashboard/   仪表盘统计
    └── export/      数据导出（CSV + 审计 + 限流）
```

每个模块内部统一五层：`controller → service → repository → entity → dto`。

---

## 第四层：分层架构 — 数据如何流动？

以「查询患者列表」为例：

```
HTTP请求 → Controller → Service → Repository → 数据库
                          ↓
                      Entity/DTO 转换（AesAttributeConverter 自动解密）
                          ↓
HTTP响应 ← Controller ← Service ← Repository ← 数据库
```

### 4.1 Controller 层 — 只做三件事

```java
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<PatientVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(patientService.page(page, size, keyword));
    }
}
```

Controller 只负责：接受参数 → 调用 Service → 包装 Result 返回。不做任何业务逻辑。

### 4.2 Service 层 — 业务逻辑，三个核心注解

```java
@Service
@RequiredArgsConstructor
public class PatientService {

    @Transactional
    @Auditable(module = "patient", action = "CREATE", phiAccess = true)
    public void create(PatientFormDTO dto) {
        patientRepository.save(dto.toEntity());
    }
}
```

| 注解 | 作用 |
|------|------|
| `@Transactional` | 事务保护 — 失败自动回滚 |
| `@Auditable(phiAccess = true)` | 异步审计日志 — 参数值自动掩码为 `[PHI]` |
| 无 `@Cacheable` | 患者 ePHI **不缓存**到 Redis — 防止解密数据在缓存中泄露 |

### 4.3 Repository 层 — Spring Data JPA

```java
public interface PatientRepository
        extends JpaRepository<Patient, Long>,
                JpaSpecificationExecutor<Patient> {
}
```

继承 `JpaSpecificationExecutor` 支持动态条件组合查询（如按 MRN 或 email 搜索）。

### 4.4 Entity 层 — 数据模型

```java
@Entity
@Table(name = "patient")
@SQLDelete(sql = "UPDATE patient SET is_deleted = 1 WHERE id = ?")   // 软删除
@SQLRestriction("is_deleted = 0")                                     // 自动过滤已删数据
public class Patient extends BaseEntity {

    private String mrn;           // Medical Record Number（内部标识）

    @Convert(converter = AesAttributeConverter.class)
    private String ssn;           // SSN — AES-256-GCM 加密存储

    @Convert(converter = AesAttributeConverter.class)
    private String name;          // 患者姓名 — 加密

    private LocalDate dateOfBirth;
    private String sexAtBirth;    // M / F / U
    private String genderIdentity;
    private String race;          // OMB 5分类（ONC USCDI 要求）
    private String ethnicity;     // Hispanic/Latino or Not

    @Convert(converter = AesAttributeConverter.class)
    private String phoneMobile;   // 手机号 — 加密

    private String email;
    // 结构化美国地址 line1/line2/city/state/zip
    // 紧急联系人、保险 payer/memberId/groupNumber
}
```

**三个核心设计：**

1. **软删除** — `@SQLDelete` + `@SQLRestriction`：`deleteById()` 只标记 `is_deleted=1`，不物理删除。所有查询自动过滤已删记录。

2. **自动加解密** — `@Convert(converter = AesAttributeConverter.class)`：加密字段写入时自动 AES-256-GCM 加密，读取时自动解密。开发者在业务层像普通字符串一样使用。

3. **乐观锁** — `BaseEntity` 中的 `@Version private Integer version`：多用户并发修改同一记录时防止数据覆盖。

### 4.5 DTO 层

```
PatientFormDTO  — 前端→后端（表单输入）
PatientVO       — 后端→前端（SSN 仅显示末4位）
```

Entity/DTO 转换逻辑封装在 DTO 内部的 `fromEntity()` / `toEntity()` 静态工厂方法里，Controller 和 Service 不散落转换代码。

---

## 第五层：安全认证 — Okta OAuth2 Resource Server

### 5.1 认证架构

```
生产环境：                        开发环境（h2/dev profile）：
┌─────────┐                      ┌──────────────────┐
│  Okta   │ ← JWKS endpoint      │ SecurityConfigDev│
│  签发JWT │    (RS256公钥)        │ 本地 JwtDecoder  │
│  MFA内置 │                      │ + JwtEncoder    │
└────┬────┘                      │ HMAC-SHA256     │
     │                           └──────────────────┘
     ▼
┌─────────────┐
│ JwtDecoder  │ ← Spring Boot 自动配置
│ 验证签名+过期 │   issuer-uri → JWKS 端点
└──────┬──────┘
       ▼
┌──────────────┐
│JwtClaimMapper│ ← Okta claims → Spring Security
│ groups→ROLE_*│   groups → ROLE_ADMIN/DOCTOR/PATIENT
│ scp→SCOPE_* │   uid→userId
└──────────────┘
```

### 5.2 登录流程

**Staff（管理员/医生）：**

```
1. POST /api/v1/auth/login { username, password }
2. AuthService 本地 BCrypt 验证 user
3. 调用 Okta /v1/token (password grant, scope: openid profile email groups)
4. Okta 返回 access_token + refresh_token
5. 前端存储 token，后续请求 Header: Authorization: Bearer <access_token>
6. Spring Security 通过 Okta JWKS 公钥验证 token 签名
7. JwtClaimMapper 提取 groups claim → ROLE_ADMIN / ROLE_DOCTOR
```

**Patient（患者）：**

```
1. POST /api/v1/patient/login { username, password }
2. 验证 PatientAuth（BCrypt）+ 账户锁定检查（5次失败→15分钟锁定）
3. 调用 Okta /v1/token (scope: openid profile groups)
4. JwtClaimMapper 提取 groups claim → ROLE_PATIENT
```

**开发环境：** `clientId == null` 时回退到本地 JwtEncoder（SecurityConfigDev），生成带 `roles` + `uid` claims 的签名 JWT。

### 5.3 Token Refresh 轮换

```
POST /api/v1/auth/refresh { refreshToken }
  → Okta /v1/token (grant_type=refresh_token)
  → 旧 refresh_token 在 Okta 侧自动失效
  → 返回新的 access_token + refresh_token
  → 开发环境不支持 refresh（重新登录即可）
```

### 5.4 RBAC 权限控制

```java
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")   // 医生或管理员可访问
@PreAuthorize("hasRole('ADMIN')")               // 仅管理员
@PreAuthorize("hasRole('PATIENT')")             // 患者门户
```

RBAC 模型：用户 → 角色 → 菜单/权限 → 接口访问控制。四张表：`sys_user`、`sys_role`、`sys_menu` + 关联表 `sys_user_role`、`sys_role_menu`。

### 5.5 SecurityConfig

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh",
                     "/api/v1/patient/login", "/api/v1/patient/refresh",
                     "/api/v1/fhir/metadata").permitAll()
    .requestMatchers("/doc.html", "/webjars/**", "/v3/api-docs/**").permitAll()
    .anyRequest().authenticated())
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtClaimMapper)));
```

**CORS：** 限制为 `app.cors.allowed-origins` 配置的特定域名（默认 `localhost:5173`），不再使用通配符 `*`。

**数据库连接：** `useSSL=true&requireSSL=true&verifyServerCertificate=true` — 数据库传输加密。

---

## 第六层：加密方案 — AesCryptoUtil + JPA Converter

### 6.1 架构设计

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

**为什么需要 static bridge？** JPA 通过 `new` 直接实例化 `AttributeConverter`，不经过 Spring 容器，所以 `@Value` 不能直接注入 Converter。方案：Spring `@Component` 持有密钥，Converter 通过静态方法访问。

### 6.2 加密字段清单

| 实体 | 加密字段 |
|------|---------|
| Patient | `name`, `ssn`, `phoneMobile`, `phoneHome`, `insuranceMemberId` |
| SysUser | `phone`, `stateLicenseNumber`, `deaNumber` |
| Message | `content`（聊天记录） |
| Bill | `insuranceClaimNumber` |
| Prescription | `deaNumber` |

### 6.3 加密算法

**AES-256-GCM / NoPadding** — GCM 模式提供认证加密（同时保证机密性和完整性），每次加密生成随机 12 字节 IV，128-bit 认证标签防止篡改。

---

## 第七层：异步审计日志 — AOP + @Async

### 7.1 架构

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

### 7.2 关键注解

```java
@Auditable(module = "patient", action = "CREATE", phiAccess = true)
```

| 属性 | 作用 |
|------|------|
| `module` | 业务模块（patient/appointment/prescription/billing/system/export） |
| `action` | 操作类型（CREATE/UPDATE/DELETE/EXPORT_PATIENTS/PAY/ADJUDICATE...） |
| `phiAccess` | 设为 true 时，方法参数值在审计日志中替换为 `[PHI]`，防止 ePHI 二次泄露 |

### 7.3 线程池配置

```java
@Bean(name = "auditExecutor")
public Executor auditExecutor() {
    // corePoolSize=2, maxPoolSize=4, queueCapacity=500
    // 队列满时 → CallerRunsPolicy（同步执行，不丢日志）
}
```

---

## 第八层：患者认证分离 — PatientAuth

### 8.1 为什么分离？

HIPAA 要求医疗记录访问与认证记录分离审计。将 `username`/`password` 从 `patient` 表移出：

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

### 8.2 账户锁定

登录失败 5 次 → 锁定 15 分钟。`isLocked()` 检查 `lockedUntil > now`。

---

## 第九层：FHIR 互操作 — HAPI FHIR 原生类型

### 9.1 架构

已从自定义 DTO 迁移到 `org.hl7.fhir.r4.model.*`（HAPI FHIR 7.4），获得 schema 验证、标准序列化（JSON/XML）和 US Core 扩展支持。

### 9.2 端点

| 端点 | 说明 |
|------|------|
| `GET /api/v1/fhir/metadata` | **CapabilityStatement** — 声明支持的 FHIR 版本(4.0.1)和资源 |
| `GET /api/v1/patients/{id}/case` | 患者完整病历 **Bundle** — Patient + Condition + AllergyIntolerance + Encounter[] + MedicationRequest[] |

### 9.3 FHIR 编码

| 编码系统 | 用途 |
|---------|------|
| `http://hl7.org/fhir/sid/us-ssn` | SSN 标识符 |
| `http://hl7.org/fhir/sid/us-mrn` | MRN 标识符 |
| `http://hl7.org/fhir/sid/ndc` | FDA National Drug Code |
| `http://www.nlm.nih.gov/research/umls/rxnorm` | RxNorm 概念编码 |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-race` | US Core Race 扩展 |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity` | US Core Ethnicity 扩展 |

---

## 第十层：业务模块美国化

### 10.1 患者 (Patient)

| 旧字段(中国) | 新字段(美国) |
|-------------|-------------|
| `id_card` | `mrn` + `ssn` |
| `age` | `dateOfBirth` |
| `gender` (0/1) | `sexAtBirth` (M/F/U) + `genderIdentity` |
| `phone` | `phoneMobile` + `phoneHome` + `phoneWork` |
| `address` (单字符串) | `addressLine1` + `addressLine2` + `city` + `state` + `zipCode` |
| — | `race` (OMB), `ethnicity`, `preferredLanguage` |
| — | `insurancePayer`, `insuranceMemberId`, `insuranceGroupNumber` |
| — | `emergencyContactName/Phone/Relation` |
| — | `primaryCareProvider`, `patientStatus`, `maritalStatus` |

### 10.2 医护人员 (SysUser)

新增 NPI(10位) / DEA Number / 州执照 + 州 / Taxonomy Code / Credentials(MD/DO/NP) / Specialty。

### 10.3 预约 (Appointment)

| 新增字段 | 值域 |
|---------|------|
| `visitType` | NEW_PATIENT / FOLLOW_UP / ANNUAL_PHYSICAL / URGENT_CARE / CONSULTATION |
| `chiefComplaint` | 主诉（就诊原因） |
| `department` | Cardiology / Family Medicine / Allergy & Immunology |
| `duration` | 预约时长（分钟） |
| `cptCode` | CPT E&M 编码 (99201-99499) |
| `status` | **0=scheduled, 1=arrived, 2=cancelled, 3=completed, 4=no-show, 5=rescheduled, 6=in-progress** |

### 10.4 处方 (Prescription)

| 新增字段 | 用途 |
|---------|------|
| `ndcCode` / `rxnormCode` | FDA + NIH 药品编码（eRx 必备） |
| `refills` / `daysSupply` | 续方次数 / 发药天数 |
| `daw` | Dispense As Written（0=允许替换, 1=按处方发药） |
| `route` | 给药途径（PO/IV/IM/INH/SL） |
| `sig` | 完整服药说明文本 |
| `icd10Codes` | ICD-10-CM 诊断编码 |
| `prescriberNpi` / `deaNumber` / `controlledSchedule` | 处方医师 NPI + DEA + 管制等级 |
| `pharmacyName/Phone/Npi` | 发药药房 |
| `prescriptionType` | MEDICATION / LAB / PROCEDURE / DME |
| `rxStatus` | active / completed / discontinued / on-hold |

### 10.5 账单 (Billing)

| 新增字段 | 用途 |
|---------|------|
| `claimStatus` | DRAFT → SUBMITTED → PENDING → PAID / DENIED → APPEALED |
| `totalCharge` / `insuranceAdjustment` / `insurancePayment` / `patientResponsibility` | 保险理赔计算 |
| `cptCodes` / `icd10Codes` / `placeOfServiceCode` | 编码 |
| `billingProviderNpi` / `renderingProviderNpi` | 计费/服务医师 NPI |
| `insuranceClaimNumber`(加密) / `priorAuthorizationNumber` | 理赔号 / 预授权 |
| `paymentMethod` / `receiptNumber` | 支付方式 / 收据号 |

BillService 提供完整理赔生命周期：`submitClaim()` → `adjudicate()` → `pay()` / `denyClaim()`。

---

## 第十一层：基础设施

### 11.1 BaseEntity — 公共父类

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;

    @Version
    private Integer version;  // 乐观锁

    @PrePersist  protected void onCreate() { ... }
    @PreUpdate   protected void onUpdate() { ... }
}
```

所有 Entity 继承它，自动获得 id、时间戳、软删除、乐观锁。

### 11.2 统一响应格式

```java
// 成功
Result.ok(data)  → { "code": 200, "message": "ok", "data": {...} }

// 失败（正确 HTTP 状态码）
Result.fail(404, "Patient not found")
  → HTTP 404  + { "code": 404, "message": "Patient not found" }
```

`GlobalExceptionHandler` 将 `BusinessException` 的 code 映射为正确 HTTP 状态码（401/403/404/409/400）。

### 11.3 限流

| 端点 | 限制 |
|------|------|
| `/api/v1/auth/login` | 10次/分钟/IP |
| `/api/v1/patient/login` | 10次/分钟/IP |
| `/api/v1/export/*` | 5次/小时/IP |

基于 Redisson `RRateLimiter`，超限返回 HTTP 429。

### 11.4 缓存

患者 ePHI **不缓存**到 Redis。仅缓存：
- Dashboard 统计数据（`@Cacheable("dashboard")`）
- SysUser 基本信息（角色/菜单）

### 11.5 结构化日志

`logback-spring.xml`：
- **生产环境** — JSON 格式（SIEM 友好），单独 `logs/audit.log`（保留 90 天）
- **开发环境** — 人类可读格式
- Hibernate SQL 日志关闭（`WARN` 级别）

### 11.6 测试

9 个单元测试覆盖：
- `AesAttributeConverter` — 加密/解密/不同 IV/损毁数据降级
- `GlobalExceptionHandler` — 401/404/409 状态码映射
- `BaseEntity` — `@Version` 字段 + `@PrePersist` 回调

---

## 第十二层：完整请求生命周期

以 `admin` 访问患者列表为例：

```
1. Filter 层
   - CorsFilter: 验证 Origin
   - RateLimiter: 非登录接口无限制

2. Spring Security 层
   - 从 Header 取出 "Bearer <access_token>"
   - 生产: Okta JWKS 公钥验证签名和过期
   - 开发: SecurityConfigDev 本地 HMAC 验证
   - JwtClaimMapper: groups=["ADMIN"] → ROLE_ADMIN
   - @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')") → 通过 ✓

3. Controller 层
   - PatientController.page(1, 10, "Anderson")

4. Service 层
   - PatientService.page()
   - Specification: mrn LIKE '%Anderson%' OR email LIKE '%Anderson%'
   - 注意: name 和 phone 已加密，不能 LIKE 搜索
   - patientRepository.findAll(spec, pageable)

5. Repository 层
   - JPA 生成 SQL:
     SELECT * FROM patient
     WHERE is_deleted=0 AND version IS NOT NULL
     AND (mrn LIKE '%Anderson%' OR email LIKE '%Anderson%')
     LIMIT 10 OFFSET 0
   - @SQLRestriction("is_deleted = 0") 自动生效
   - AesAttributeConverter 自动解密 name, ssn, phoneMobile 等字段

6. 返回
   - Entity → PatientVO.fromEntity()
   - SSN 仅显示末4位 (maskLast4)
   - PageResult + Result 封装
   - Jackson 序列化 JSON
```

---

## 总结：设计亮点

| 亮点 | 实现 |
|------|------|
| **HIPAA 加密** | AES-256-GCM + JPA @Convert 透明加解密 + 损毁降级 |
| **HIPAA 审计** | 异步 AOP (REQUIRES_NEW tx) + PHI 掩码 + 专用线程池 |
| **Okta OAuth2** | 生产 JWKS 验证 + 开发本地回退 + Token 刷新轮换 |
| **FHIR 标准** | HAPI FHIR 原生类型 + CapabilityStatement + USCDI 扩展 |
| **US 医疗合规** | 5 模块美式字段(NPI/NDC/CPT/ICD-10/POS/USCDI demographics) |
| **软删除** | @SQLDelete + @SQLRestriction |
| **乐观锁** | @Version on BaseEntity |
| **账户安全** | BCrypt + 登录限流 + PatientAuth 5次锁定 + 导出限流 |
| **数据最小化** | VO SSN 末4位 + 审计 PHI 掩码 + 缓存排除 ePHI |
| **分层解耦** | Entity/DTO 分离 + static bridge 解决 JPA/Spring DI 冲突 |
| **开箱即用** | DataInitializer JPA 写入 + US 合成种子数据 |
