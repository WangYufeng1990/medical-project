# Round 5–9: 医疗进阶功能规划

> 基于当前 HIPAA + FHIR + US-Model 三支柱已完成，向临床决策支持、检验互操作、电子处方方向扩展。

---

## Round 5: CDS — 临床决策支持（Drug-Drug / Drug-Allergy 交互检查）

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

## Round 6: 集成引擎对接 — ADT + 检验结果 JSON API

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

## Round 7: LOINC 检验编码 + 异常标识 + 趋势分析

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

## Round 8: ePrescribing — 电子处方

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

## Round 9: eCQM — 临床质量度量

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

## 执行优先级

```
Round 5  CDS (Drug-Drug + Drug-Allergy)          ← 最高优先，面试最常问
Round 6  集成引擎对接 (ADT + Lab Results JSON)      ← 医院集成必备
Round 7  LOINC 编码 + 异常标识 + 趋势分析          ← 依赖 Round 6 的 observation 表
Round 8  ePrescribing + EPCS                      ← 法规要求但不紧急
Round 9  eCQM 临床质量度量                        ← 依赖前面所有模块的数据
```

**建议路径：**
```
当前项目 → Round 5 (CDS) → Round 6 (Integration API) → Round 7 (LOINC)
                         ↘ Round 8 (eRx)              → Round 9 (eCQM)
```
CDS 和 Integration 是独立的两条线，可以在 CDS 之后并行或顺序做。

---

## 新增 Maven 依赖预估

无需新增依赖。所有 Rounds 纯业务逻辑 + 现有 Spring Boot/HAPI FHIR 栈即可实现。

---

## 总文件预估

| Round | 新建文件 | 修改文件 |
|-------|---------|---------|
| 5 CDS | ~8 | 2 |
| 6 Integration | ~7 | 1 |
| 7 LOINC | ~4 | 1 |
| 8 ePrescribing | ~6 | 1 |
| 9 eCQM | ~5 | 1 |
| **合计** | **~30** | **6** |

---

## 下一步

准备好后说"开始 Round 5"，我会按 CDS → HL7 v2 → LOINC 的顺序依次实现。
