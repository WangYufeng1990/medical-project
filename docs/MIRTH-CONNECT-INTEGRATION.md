# Mirth Connect Integration

本系统通过 REST JSON API 接收 Mirth Connect 转发的医疗数据。Mirth Connect 作为协议适配器，负责将上游 HL7 v2.x 消息转换为约定的 JSON 结构后 POST 到后端端点。

---

## 端点总览

| 端点 | Method | Content-Type | 认证 |
|------|--------|-------------|------|
| `/api/v1/integration/adt` | POST | `application/json` | Bearer token (ADMIN/DOCTOR) |
| `/api/v1/integration/lab-results` | POST | `application/json` | Bearer token (ADMIN/DOCTOR) |

---

## 认证配置

在 Mirth Connect Destination 的 HTTP Sender 设置中添加 Header：

```
Name:  Authorization
Value: Bearer <jwt-token>
```

Token 需来自具有 `ADMIN` 或 `DOCTOR` 角色的账户。开发环境可使用默认 `doctor1` 账户登录获取 token（`POST /api/v1/auth/login`）。

---

## ADT Channel

### 数据模型

**请求体结构（对应 HL7 ADT 消息的 PID/PV1 段）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sourceMessageId` | string | 是 | 来源消息 ID（MSH-10） |
| `eventType` | string | 是 | ADT 事件类型，如 A01/A03/A08 |
| `eventTime` | string | 否 | 事件发生时间（MSH-7） |
| `patient.mrn` | string | 是 | 患者病历号（PID-3） |
| `patient.name` | string | 否 | 患者姓名 |
| `patient.dateOfBirth` | string | 否 | 出生日期 |
| `patient.sexAtBirth` | string | 否 | 性别（M/F/U） |
| `patient.address.line1` | string | 否 | 地址 |
| `patient.address.city` | string | 否 | 城市 |
| `patient.address.state` | string | 否 | 州/省 |
| `patient.address.zip` | string | 否 | 邮编 |
| `visit.visitNumber` | string | 否 | 就诊号（PV1-19） |
| `visit.admitDate` | string | 否 | 入院日期（PV1-44） |
| `visit.dischargeDate` | string | 否 | 出院日期（PV1-45） |
| `visit.department` | string | 否 | 科室（PV1-3） |
| `visit.admittingDoctorNpi` | string | 否 | 主治医生 NPI（PV1-7） |

### Destination Connector 配置

```
Connector Type: HTTP Sender
URL:            https://<host>/api/v1/integration/adt
Method:         POST
Content-Type:   application/json
```

### Transformer (JavaScript) — HL7 v2.x → JSON

```javascript
var hl7 = new XML(SerializerFactory.getSerializer('HL7V2').toXML(msg));

var json = {
    sourceMessageId: hl7['MSH']['MSH.10']['MSH.10.1'].toString(),
    eventType:       hl7['MSH']['MSH.9']['MSH.9.2'].toString(),
    eventTime:       hl7['MSH']['MSH.7']['MSH.7.1'].toString(),
    patient: {
        mrn:         hl7['PID']['PID.3']['PID.3.1'].toString(),
        name:        hl7['PID']['PID.5']['PID.5.2'].toString() + ' ' +
                     hl7['PID']['PID.5']['PID.5.1'].toString(),
        dateOfBirth: hl7['PID']['PID.7']['PID.7.1'].toString(),
        sexAtBirth:  hl7['PID']['PID.8']['PID.8.1'].toString(),
        address: {
            line1: hl7['PID']['PID.11']['PID.11.1'].toString(),
            city:  hl7['PID']['PID.11']['PID.11.3'].toString(),
            state: hl7['PID']['PID.11']['PID.11.4'].toString(),
            zip:   hl7['PID']['PID.11']['PID.11.5'].toString()
        }
    },
    visit: {
        visitNumber:        hl7['PV1']['PV1.19']['PV1.19.1'].toString(),
        admitDate:          hl7['PV1']['PV1.44']['PV1.44.1'].toString(),
        dischargeDate:      hl7['PV1']['PV1.45']['PV1.45.1'].toString(),
        department:         hl7['PV1']['PV1.3']['PV1.3.2'].toString(),
        admittingDoctorNpi: hl7['PV1']['PV1.7']['PV1.7.1'].toString()
    }
};

channelMap.put('adtPayload', JSON.stringify(json));
```

### 后端处理逻辑

- 按 `patient.mrn` 查找患者：存在则合并更新字段，不存在则新建 Patient 记录。
- Patient 敏感字段（姓名、地址等）通过 `@Convert(converter = AesAttributeConverter.class)` 自动 AES-256-GCM 加密落库。

---

## Lab Results Channel

### 数据模型

**请求体结构（对应 HL7 ORU^R01 消息的 OBR/OBX 段）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sourceMessageId` | string | 是 | 来源消息 ID，用于幂等去重 |
| `patientMrn` | string | 是 | 患者 MRN |
| `orderCode` | string | 否 | 医嘱编号（OBR-4） |
| `collectionDate` | string | 否 | 采样日期（OBR-7） |
| `results` | array | 是 | 检验结果列表，至少 1 条 |
| `results[].loincCode` | string | 是 | LOINC 编码（OBX-3.1） |
| `results[].display` | string | 否 | LOINC 显示名称（OBX-3.2） |
| `results[].value` | string | 否 | 结果值（OBX-5） |
| `results[].unit` | string | 否 | 单位（OBX-6） |
| `results[].referenceRange` | string | 否 | 参考范围（OBX-7） |
| `results[].abnormalFlag` | string | 否 | 异常标志：L=偏低 H=偏高 N=正常 A=异常 |

### Destination Connector 配置

```
Connector Type: HTTP Sender
URL:            https://<host>/api/v1/integration/lab-results
Method:         POST
Content-Type:   application/json
```

### Transformer (JavaScript) — HL7 ORU^R01 → JSON

```javascript
var hl7 = new XML(SerializerFactory.getSerializer('HL7V2').toXML(msg));

var results = [];
for each (var obx in hl7['ORU_R01_PATIENT_RESULT']['ORU_R01_ORDER_OBSERVATION']) {
    var obxGroup = obx['ORU_R01_OBSERVATION'];
    results.push({
        loincCode:      obxGroup['OBX']['OBX.3']['OBX.3.1'].toString(),
        display:        obxGroup['OBX']['OBX.3']['OBX.3.2'].toString(),
        value:          obxGroup['OBX']['OBX.5']['OBX.5.1'].toString(),
        unit:           obxGroup['OBX']['OBX.6']['OBX.6.1'].toString(),
        referenceRange: obxGroup['OBX']['OBX.7']['OBX.7.1'].toString(),
        abnormalFlag:   obxGroup['OBX']['OBX.8']['OBX.8.1'].toString()
    });
}

var json = {
    sourceMessageId: hl7['MSH']['MSH.10']['MSH.10.1'].toString(),
    patientMrn:      hl7['PID']['PID.3']['PID.3.1'].toString(),
    orderCode:       hl7['OBR']['OBR.4']['OBR.4.1'].toString(),
    collectionDate:  hl7['OBR']['OBR.7']['OBR.7.1'].toString(),
    results:         results
};

channelMap.put('labPayload', JSON.stringify(json));
```

### 幂等去重

后端通过 `sourceMessageId` 做幂等：已处理过的消息直接返回 `recordsCreated: 0`，不会生成重复 Observation 记录。Mirth Connect 可以安全重试失败的投递。

### 响应格式

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "status": "ACK",
    "recordsCreated": 3,
    "sourceMessageId": "MSG-20240624-001"
  }
}
```

---

## 重试与容错

| 配置项 | 建议值 | 说明 |
|--------|--------|------|
| Queuing | On (DISPATCHED) | 投递失败的消息进入队列，不丢失 |
| Retry Interval | 30s × 3, then 5min | 先密集重试，再拉长间隔 |
| Rotate Queue | On | 避免单个队列文件过大 |
| Error Response | 4xx/5xx 触发重试 | HTTP Sender 默认行为 |

Response Transformer 中可检查 ACK 状态：

```javascript
var response = JSON.parse(connectorMessage.getResponseData());
if (response.data && response.data.status === 'ACK') {
    // 标记为成功，从队列移除
    return;
}
// 否则 Mirth Connect 按重试策略处理
```

---

## Mirth Connect 版本兼容

- 适配 **NextGen Connect 3.x**（原 Mirth Connect 3.x），使用 Rhino JS 引擎。
- 如果使用 NextGen Connect 4.x（Nashorn → GraalVM JS），上述 Transformer 代码兼容，但需要确保 `SerializerFactory` 及 HL7 扩展已安装。
