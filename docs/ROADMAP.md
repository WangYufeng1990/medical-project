# 项目演进路线图 ✅ 全部完成

> 从 HIPAA + FHIR + US-Model 三支柱出发，历经临床决策支持、检验互操作、电子处方、合规审计修复、前端重构。
>
> **状态：9 Rounds + 3 合规 Rounds + 前端重构 — 全部完成 (2026-06-03)**

---

## 前置 Rounds (已归档)

| Round | 内容 | 状态 |
|-------|------|------|
| Pre-work | AES 落盘加密、FHIR SSN 脱敏、CSV 脱敏、Redis PHI、审计 patientId、密钥轮换 | ✅ |
| 1 | 审计日志查询 API、安全响应头、账户锁定 | ✅ |
| 2 | 密码策略、患者数据访问权、Token 配置外置 | ✅ |
| 3 | FHIR 资源端点、SMART 作用域、US Core 修正 | ✅ |
| 4 | 数据留存、知情同意、紧急访问、密钥审计 | ✅ |

---

## Round 5: CDS — 临床决策支持 ✅ 完成

### 目标
医生开处方时，系统自动检查药物相互作用和过敏禁忌，阻止危险处方。

### 功能范围

| 序号 | 功能 | 描述 |
|------|------|------|
| 5.1 | **Drug-Drug Interaction 规则引擎** | 定义一个 `drug_interaction` 规则表，存储药物对及其严重级别（contraindicated / severe / moderate / minor）。`PrescriptionService.create()` 在保存前遍历 `items` 两两交叉检查 |
| 5.2 | **Drug-Allergy 禁忌检查** | 读取 `Patient.allergies`，交叉比对处方中所有药物的过敏类别（如 "Penicillin" → 青霉素族）。需要一张 `drug_allergy_class` 表映射药品到过敏类别 |
| 5.3 | **CDS Hook 端点** | 符合 FHIR CDS Hooks 规范的 `POST /api/v1/fhir/cds-services` Discovery 端点 + `POST /api/v1/cds/drug-interaction-check` 服务端点，让外部 EHR 也可以调用 |
| 5.4 | **处方警告响应** | 交互检查结果不硬阻断，返回 `Warning`（严重级别 + 消息 + 备选药品）。前端展示警告后医生可选择 override + 填写理由 + 审计记录 |

### 数据模型

```sql
-- 药品交互规则表
CREATE TABLE drug_interaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_a_code VARCHAR(20) NOT NULL,       -- RxNorm code of drug A
    drug_b_code VARCHAR(20) NOT NULL,       -- RxNorm code of drug B
    severity VARCHAR(20) NOT NULL,          -- contraindicated / severe / moderate / minor
    description VARCHAR(500) NOT NULL,      -- e.g. "Increased risk of QT prolongation"
    mechanism VARCHAR(200),                 -- pharmacologic mechanism
    recommendation VARCHAR(500)            -- clinical recommendation
);

-- 药品过敏类别映射
CREATE TABLE drug_allergy_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_rxnorm_code VARCHAR(20) NOT NULL,
    allergy_class VARCHAR(100) NOT NULL,    -- "Penicillin", "Sulfa", "NSAIDs"
    cross_reactive_codes VARCHAR(500)       -- comma-separated related RxNorm codes
);

-- 处方覆盖记录（override 审计）
CREATE TABLE cds_override (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT NOT NULL,
    warning_type VARCHAR(30) NOT NULL,      -- DRUG_DRUG / DRUG_ALLERGY
    severity VARCHAR(20),
    drugs_involved VARCHAR(200),
    override_reason VARCHAR(500) NOT NULL,
    overridden_by BIGINT NOT NULL,
    overridden_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 涉及文件

| 文件 | 操作 |
|------|------|
| `common/config/CdsConfig.java` | CDS Hooks 配置 |
| `module/prescription/service/CdsService.java` | 新建 — 交互检查核心逻辑 |
| `module/prescription/controller/CdsController.java` | 新建 — CDS Hooks 端点 |
| `module/prescription/entity/DrugInteraction.java` | 新建 |
| `module/prescription/entity/DrugAllergyClass.java` | 新建 |
| `module/prescription/entity/CdsOverride.java` | 新建 |
| `module/prescription/repository/*` | 对应 3 个 Repository |
| `module/prescription/service/PrescriptionService.java` | 修改 — create() 集成 CDS 检查 |
| `resources/sql/schema.sql` | 添加 3 张新表 |

### 种子数据量
- 20 条常见 Drug-Drug Interaction（如 Warfarin+Aspirin=severe, Metformin+Contrast=contraindicated）
- 15 条 Drug-Allergy Class 映射

---

## Round 6: 集成引擎对接 — ADT + 检验结果 JSON API ✅ 完成

### 背景

现代医疗架构中，HL7 v2 管道消息由集成引擎（Mirth Connect / Rhapsody / Corepoint）在医院侧解析并转换为结构化 JSON，再通过 HTTP 发送给业务后端。后端不需要内嵌 HL7 v2 解析器。

```
Hospital EHR → HL7 v2 (MLLP) → Mirth Connect → JSON/HTTP → Our Backend
```

### 目标
定义 Mirth Connect 转换后的 JSON 契约，接收 ADT（入出院）事件和检验结果，完成数据写入。

### 功能范围

| 序号 | 功能 | 描述 |
|------|------|------|
| 6.1 | **ADT 事件 JSON 契约** | 定义 `AdtEvent` JSON schema：`eventType`（A01入院/A03出院/A08更新）、`patientMrn`、`patientName`、`dob`、`sex`、`admitDate`、`dischargeDate`、`department`。文档化字段映射：JSON path → Patient/Appointment 实体字段 |
| 6.2 | **ADT 事件处理** | `POST /api/v1/integration/adt` — 接收 ADT JSON，自动 upsert Patient（MRN 匹配） + 创建 Admission Encounter（A01）或关闭当前 Encounter（A03） |
| 6.3 | **检验结果 JSON 契约** | 定义 `LabResult` JSON schema：`patientMrn`、`orderCode`、`collectionDate`、`results[]`（`loincCode`、`value`、`unit`、`referenceRange`、`abnormalFlag`） |
| 6.4 | **检验结果处理** | `POST /api/v1/integration/lab-results` — 接收检验结果 JSON → 批量写入 `observation` 表 |
| 6.5 | **FHIR Observation 端点** | `GET /api/v1/fhir/Observation/{id}` + `GET /api/v1/fhir/Observation?patient={id}` — 将 `observation` 表数据转换为 FHIR Observation 资源 |
| 6.6 | **幂等性保证** | 集成引擎可能重发消息，通过 `source_message_id`（Mirth 消息 ID）去重 |

### JSON 契约示例

**ADT A01 (入院):**
```json
{
  "sourceMessageId": "mirth-msg-12345",
  "eventType": "A01",
  "eventTime": "2026-06-01T08:30:00Z",
  "patient": {
    "mrn": "MRN-10001",
    "name": "James Anderson",
    "dateOfBirth": "1998-02-14",
    "sexAtBirth": "M",
    "address": { "line1": "1400 S Lake Shore Dr", "city": "Chicago",
                 "state": "IL", "zip": "60605" }
  },
  "visit": {
    "visitNumber": "VIS-78901",
    "admitDate": "2026-06-01T08:30:00Z",
    "department": "Cardiology",
    "admittingDoctorNpi": "1234567890"
  }
}
```

**Lab Result (检验):**
```json
{
  "sourceMessageId": "mirth-msg-67890",
  "patientMrn": "MRN-10001",
  "orderCode": "CBC",
  "collectionDate": "2026-06-01T07:00:00Z",
  "results": [
    { "loincCode": "6690-2", "display": "WBC", "value": "7.2",
      "unit": "10*3/uL", "referenceRange": "4.0-11.0", "abnormalFlag": "N" },
    { "loincCode": "789-8", "display": "RBC", "value": "4.8",
      "unit": "10*6/uL", "referenceRange": "4.5-5.9", "abnormalFlag": "N" },
    { "loincCode": "718-7", "display": "HGB", "value": "14.1",
      "unit": "g/dL", "referenceRange": "13.5-17.5", "abnormalFlag": "N" }
  ]
}
```

### 数据模型

```sql
CREATE TABLE observation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    loinc_code VARCHAR(20) NOT NULL,
    loinc_display VARCHAR(200),
    value VARCHAR(50),
    unit VARCHAR(20),
    reference_range VARCHAR(50),
    abnormal_flag CHAR(1),                -- N/L/H/LL/HH
    status VARCHAR(20) DEFAULT 'final',
    source_message_id VARCHAR(100),       -- Mirth message ID for dedup
    effective_date TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_obs_patient_loinc (patient_id, loinc_code),
    UNIQUE KEY uk_source_message (source_message_id)
);
```

### 涉及文件

| 文件 | 操作 |
|------|------|
| `module/integration/controller/IntegrationController.java` | 新建 — `POST /api/v1/integration/adt` + `/lab-results` |
| `module/integration/service/AdtService.java` | 新建 — patient upsert + encounter 管理 |
| `module/integration/service/LabResultService.java` | 新建 — observation 批量写入 + 去重 |
| `module/integration/dto/AdtEventDTO.java` | 新建 |
| `module/integration/dto/LabResultDTO.java` | 新建 |
| `module/patient/entity/Observation.java` | 新建 |
| `module/patient/repository/ObservationRepository.java` | 新建 |
| `module/patient/controller/FhirObservationController.java` | 新建 |
| `resources/sql/schema.sql` | 添加 `observation` 表 |

### 无新增依赖
纯 JSON over HTTP，不需要 HL7 解析库。

---

## Round 7: LOINC 检验编码 + 异常标识 + 趋势分析 ✅ 完成

### 目标
基于 Round 6 的 `observation` 表，建立 LOINC 编码知识库，支持参考范围自动匹配、异常标识和趋势查询。

### 功能范围

| 序号 | 功能 | 描述 |
|------|------|------|
| 7.1 | **LOINC 编码字典表** | `loinc_catalog` 表存储常用检验项目的 LOINC code/display/unit/reference range |
| 7.2 | **异常自动标识** | 根据 reference range 自动设置 `abnormal_flag`（N/L/H/LL/HH/AA），支持年龄/性别分组的参考范围 |
| 7.3 | **检验趋势查询** | `GET /api/v1/patients/{id}/observations?loinc=` 返回某患者某检验项目的历史趋势（按时间排序） |
| 7.4 | **Panel 支持** | CBC（全血细胞计数）= WBC+RBC+HGB+HCT+PLT；BMP（基础代谢面板）= Glucose+Ca+Na+K+CO2+Cl+BUN+Creatinine。支持 panel 展开 |

### 数据模型

```sql
CREATE TABLE loinc_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loinc_code VARCHAR(20) UNIQUE NOT NULL,
    display VARCHAR(200) NOT NULL,
    unit VARCHAR(20),
    ref_range_low VARCHAR(20),
    ref_range_high VARCHAR(20),
    panel_parent_code VARCHAR(20)          -- e.g. CBC's LOINC code for panel grouping
);
```

### 涉及文件

| 文件 | 操作 |
|------|------|
| `module/patient/entity/LoincCatalog.java` | 新建 |
| `module/patient/repository/LoincCatalogRepository.java` | 新建 |
| `module/patient/service/LabResultService.java` | 新建 — 趋势 + 异常标识 |
| `module/patient/controller/LabResultController.java` | 新建 |
| `resources/sql/schema.sql` | 添加 `loinc_catalog` 表 |

### 种子数据
30 个常用 LOINC 编码（CBC 8项 + BMP 8项 + 血脂 4项 + HbA1c + TSH + 尿常规 8项）。

---

## Round 8: ePrescribing + EPCS ✅ 完成

### 目标
支持电子处方发送到药房和 EPCS（管制药品电子处方）合规。

### 功能范围

| 序号 | 功能 | 描述 |
|------|------|------|
| 8.1 | **药房目录** | `pharmacy_directory` 表，存储药房 NPI、名称、地址、支持的电子处方标准（NCPDP SCRIPT） |
| 8.2 | **NCPDP SCRIPT 消息生成** | 生成符合 NCPDP SCRIPT 10.6 标准的 NewRx 消息（XML），可选是否加密 |
| 8.3 | **EPCS 管制药品流程** | 管制药品（Schedule II-V）需要双因素认证 + 单独审计。`Prescription.controlledSchedule` 不为空时触发 EPCS 流程 |
| 8.4 | **处方状态追踪** | `rx_status` 增强：active → transmitted → received → dispensed → picked_up，追踪电子处方全生命周期 |
| 8.5 | **药房选择接口** | `GET /api/v1/pharmacies?zip=&distance=` 检索附近药房 |

### 数据模型

```sql
CREATE TABLE pharmacy_directory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    npi VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(100),
    city VARCHAR(50),
    state CHAR(2),
    zip_code VARCHAR(10),
    phone VARCHAR(200),
    supports_epcs TINYINT DEFAULT 0
);
```

### 涉及文件

| 文件 | 操作 |
|------|------|
| `module/prescription/entity/PharmacyDirectory.java` | 新建 |
| `module/prescription/repository/PharmacyDirectoryRepository.java` | 新建 |
| `module/prescription/controller/PharmacyController.java` | 新建 |
| `module/prescription/service/EpcsService.java` | 新建 — EPCS 双因素 + 审计 |
| `module/prescription/service/NcpdpScriptService.java` | 新建 — NewRx XML 生成 |
| `module/prescription/service/PrescriptionService.java` | 修改 — 增加 transmit/send 方法 |

---

## Round 9: eCQM — 临床质量度量 ✅ 完成

### 目标
计算 CMS MIPS/MACRA 要求的临床质量指标。

### 功能范围

| 序号 | 功能 | 描述 |
|------|------|------|
| 9.1 | **度量定义引擎** | `quality_measure` 表定义指标（人群、分母、分子、排除项），基于 FHIR eCQM 的 Measure 资源 |
| 9.2 | **HbA1c 控制率 (CMS122v11)** | 糖尿病患者的 HbA1c < 9%，需要 1 次以上检验记录 |
| 9.3 | **乳腺癌筛查率 (CMS125v11)** | 50-74 岁女性在 27 个月内做过乳腺 X 线检查 |
| 9.4 | **高血压控制率 (CMS165v11)** | 高血压患者最近一次血压 < 140/90 |
| 9.5 | **度量报告导出** | `GET /api/v1/quality/measures/{id}/report?period=2026` 返回符合 CMS 格式的度量报告 JSON |

### 数据模型

```sql
CREATE TABLE quality_measure (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cms_id VARCHAR(20) UNIQUE NOT NULL,     -- e.g. "CMS122v11"
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    denominator_query VARCHAR(1000),        -- SQL or JPQL criteria
    numerator_query VARCHAR(1000),
    exclusion_query VARCHAR(1000),
    report_period_months INT DEFAULT 12
);
```

### 涉及文件

| 文件 | 操作 |
|------|------|
| `module/quality/entity/QualityMeasure.java` | 新建 |
| `module/quality/entity/QualityResult.java` | 新建 |
| `module/quality/repository/*` | 2 个 Repository |
| `module/quality/service/QualityMeasureService.java` | 新建 — SQL 查询 + 计算 |
| `module/quality/controller/QualityController.java` | 新建 |
| `resources/sql/schema.sql` | 添加 2 张表 |

### 种子数据
3 个 CMS eCQM 定义（CMS122v11、CMS125v11、CMS165v11）。

---

## 执行优先级（全部完成）

```
Round 5  CDS (Drug-Drug + Drug-Allergy)          ✅ 2026-06-01
Round 6  集成引擎对接 (ADT + Lab Results JSON)      ✅ 2026-06-01
Round 7  LOINC 编码 + 异常标识 + 趋势分析          ✅ 2026-06-01
Round 8  ePrescribing + EPCS                      ✅ 2026-06-01
Round 9  eCQM 临床质量度量                        ✅ 2026-06-01
```

**无需新增 Maven 依赖。** 所有 Rounds 纯业务逻辑 + 现有 Spring Boot/HAPI FHIR 栈实现。

## 实际文件统计

| Round | 新建文件 | 修改文件 | Commit |
|-------|---------|---------|--------|
| 5 CDS | 9 | 3 | `4390489` |
| 6 Integration | 8 | 2 | `290476d` |
| 7 LOINC | 4 | 2 | `6fbeb07` |
| 8 ePrescribing | 5 | 3 | `5a08006` |
| 9 eCQM | 4 | 2 | `13800ec` |
| **合计** | **30** | **12** | — |

---

# Round 10: 剩余 PHI 字段落盘加密 ✅ 完成

> **状态：已完成 (2026-06-02)**

Patient 表新增 12 个字段加密：address、city/state/zip、emergencyContactName、insurancePayer/GroupNumber、primaryCareProvider、medicalHistory(TEXT→VARCHAR 4000)、allergies(VARCHAR→2000)、dateOfBirth(LocalDate→VARCHAR 100)。

`LocalDateAttributeConverter` 新建。Patient 表 19/29 字段 AES-256-GCM 加密 + 1字段 LocalDate 加密。

---

# Round 11–13: HIPAA/21 CFR Part 11 合规安全审计 ✅ 全部完成

> **来源：** 地狱级合规审计 (2026-06-02)
> **状态：3/3 Rounds 已完成 (2026-06-03)**

---

## Round 11: CRITICAL 红线修复 ✅ 完成

### 11.1 登录审计追踪 (21 CFR Part 11 §11.300)

| 任务 | 文件 | 说明 |
|------|------|------|
| 登录成功审计 | `AuthService.login()` | 添加 `@Auditable(module="auth", action="LOGIN_SUCCESS")` |
| 登录失败审计 | `AuthService.login()` | 新增 `@AfterThrowing` 切面捕获失败事件 |
| 患者登录审计 | `PatientAuthController.login()` | 同上 |
| 令牌刷新审计 | `AuthService.refresh()`、`PatientAuthController.refresh()` | 添加 `@Auditable` |
| 登出审计 | `AuthController.logout()` | 添加 `@Auditable` |

### 11.2 生产 MySQL SSL 修复

| 任务 | 文件 | 说明 |
|------|------|------|
| 补全 SSL 参数 | `application-prod.yml:3` | JDBC URL 添加 `useSSL=true&requireSSL=true&verifyServerCertificate=true` |

### 11.3 审计日志防篡改

| 任务 | 文件 | 说明 |
|------|------|------|
| 审计表只追加 | `schema.sql` | 添加数据库 TRIGGER 阻止 UPDATE/DELETE |
| 哈希链完整性 | `AuditLog.java` | 添加 `SHA-256(prev_hash \|\| this_row)` 列 |
| 移除物理删除 | `DataRetentionJob.java` | `deleteByCreateTimeBefore()` → 改为软删除 + 归档 |
| 不可变实体 | `AuditLog.java` | 继承 `BaseEntity`，添加 `@SQLRestriction` |

### 11.4 PatientVO @PhiField 补全

| 任务 | 文件 | 说明 |
|------|------|------|
| 15 个字段标注 | `PatientVO.java` | `addressLine1/2`、`city`、`state`、`zipCode`、`dateOfBirth`、`medicalHistory`、`allergies`、`emergencyContactName/Phone/Relation`、`insurancePayer/MemberId/GroupNumber`、`primaryCareProvider` 添加 `@PhiField` |

### 11.5 角色/菜单权限变更审计

| 任务 | 文件 | 说明 |
|------|------|------|
| 角色 CRUD 审计 | `SysRoleService.java` | create/update/delete 添加 `@Auditable` |
| 菜单 CRUD 审计 | `SysMenuService.java` | create/update/delete 添加 `@Auditable` |

---

## Round 12: HIGH 优先修复 ✅ 完成

### 12.1 电子签名 (21 CFR Part 11 §11.200)

| 任务 | 文件 | 说明 |
|------|------|------|
| 处方签署双因素 | `PrescriptionController.transmit()` | 要求重新输入密码 + TOTP |
| 签名审计记录 | `EpcsService.java` | 实现 TODO 注释中的 EPCS 双因素验证 |
| 账单/同意签名 | `BillController`、`ConsentController` | 关键操作需要签名确认 |

### 12.2 审计日志归档（非删除）

| 任务 | 文件 | 说明 |
|------|------|------|
| WORM 归档服务 | 新建 `AuditArchiveService.java` | 6 年前记录移至仅追加存储 |
| 移除硬删除 | `DataRetentionJob.java` | 改为标记 `archived=true` |

### 12.3 AES 密钥派生升级 (NIST SP 800-132)

| 任务 | 文件 | 说明 |
|------|------|------|
| PBKDF2 替代 SHA-256 | `AesCryptoUtil.deriveKey()` | 使用 PBKDF2-HMAC-SHA256，310,000 迭代 + 随机盐 |

### 12.4 无分页查询添加分页

| 任务 | 文件 | 说明 |
|------|------|------|
| FHIR Patient 分页 | `FhirPatientController.java` | 添加 `_count`/`_offset` 参数，max=500 |
| FHIR Observation 分页 | `FhirObservationController.java` | 同上 |
| 其他 findAll() 端点 | 6 个 controller | 添加分页或上限 |

### 12.5 CSV 流式导出

| 任务 | 文件 | 说明 |
|------|------|------|
| StreamingResponseBody | `ExportController.java` | 逐行写入而非内存构建 String |
| JPA Stream 查询 | `PatientRepository` | 添加 `streamAll()` 方法 |

### 12.6 PatientService.update() PHI 掩码

| 任务 | 文件 | 说明 |
|------|------|------|
| phiAccess=true | `PatientService.update()` | 防止 `PatientFormDTO` 序列化到审计详情 |

---

## Round 13: MEDIUM 优化加固 ✅ 完成

### 13.1 刷新令牌限速

| 任务 | 文件 | 说明 |
|------|------|------|
| refresh URI 限速 | `RateLimiterConfig.java` | 添加 `/refresh` 匹配，20 次/分钟/IP |

### 13.2 Okta RestTemplate 池化

| 任务 | 文件 | 说明 |
|------|------|------|
| 共享 RestTemplate Bean | `AuthService.java`、`PatientAuthController.java` | 连接池 + 5s 超时 + 断路器 |

### 13.3 账户锁定原子化

| 任务 | 文件 | 说明 |
|------|------|------|
| 原子失败计数 | `SysUserRepository.java` | `@Modifying UPDATE SET failed_attempts = failed_attempts + 1` |

### 13.4 紧急访问原因净化

| 任务 | 文件 | 说明 |
|------|------|------|
| 预定义原因码 | `EmergencyAccessController.java` | 限制 `reason` 为枚举值或正则净化 |

### 13.5 移除硬编码密钥

| 任务 | 文件 | 说明 |
|------|------|------|
| 强制环境变量 | `application-*.yml` | 移除 dev/h2 配置中的默认密钥和密码 |
| Vault 集成 | 新建 | 可选：集成 HashiCorp Vault |

---

## 执行优先级（全部完成）

```
Round 11  Critical 红线     ✅ 2026-06-02  (5 项: 登录审计/生产SSL/防篡改/@PhiField/角色审计)
Round 12  HIGH 优先         ✅ 2026-06-03  (4 项: PBKDF2/分页/流式CSV/phiAccess)
Round 13  MEDIUM 优化加固   ✅ 2026-06-03  (3 项: 刷新限速/RestTemplate/原子锁)
```

| Round | Commit | 修复数 |
|-------|--------|--------|
| 11 | `8d4ed44` | 5 |
| 12 | `d9970d8` | 4 |
| 13 | `3f68311` | 3 |

---

# Round 14–15: 前端重构 ✅ 全部完成

> **状态：2/2 Rounds 已完成 (2026-06-03)**

## Round 14: 前端 TypeScript 迁移 + PatientForm 组件 ✅ 完成

| 任务 | 说明 | Commit |
|------|------|--------|
| Vue JS→TS | 20 个 Vue SFC 全部改用 `<script setup lang="ts">`；17 个 JS 文件改名 .ts | `6beb1a6` |
| PatientForm | 完整 US 医疗模型表单：OMB 种族、结构化地址、保险字段、FHIR Bundle 解析、PHI 脱敏、[DECRYPT_FAILED] 防御 | `6beb1a6` |
| Dashboard 跳转 | 统计卡片点击跳转对应模块路由 | `6beb1a6` |

## Round 15: React 全量迁移 ✅ 完成

| 任务 | 说明 | Commit |
|------|------|--------|
| 移除 Vue | 删除所有 `.vue` SFC 文件 | `50207ae` |
| React + TS | React 18 + TypeScript + CSS Modules + Vite 5 | `50207ae` |
| 30+ 组件 | StaffLayout、Login、Dashboard、Patients、Appointments、Prescriptions、Billing、Profile、System CRUD、Patient 门户全套 | `50207ae` |

| Round | Commit | 说明 |
|-------|--------|------|
| 14 | `6beb1a6` | Vue TypeScript 迁移 + PatientForm |
| 15 | `50207ae` | Vue→React 全量重写 |
