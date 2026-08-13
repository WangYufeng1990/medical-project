# Mirth Connect Integration

This system receives medical data forwarded by Mirth Connect via REST JSON APIs. Mirth Connect acts as a protocol adapter, transforming upstream HL7 v2.x messages into an agreed-upon JSON structure before POSTing to the backend endpoints.

---

## Endpoints

| Endpoint | Method | Content-Type | Auth |
|----------|--------|-------------|------|
| `/api/v1/integration/adt` | POST | `application/json` | Bearer token (ADMIN/DOCTOR) + `X-Integration-Key` |
| `/api/v1/integration/lab-results` | POST | `application/json` | Bearer token (ADMIN/DOCTOR) + `X-Integration-Key` |

---

## Authentication

Add **two** headers in the Mirth Connect Destination HTTP Sender settings:

```
Name:  Authorization
Value: Bearer <jwt-token>
Name:  X-Integration-Key
Value: <integration-api-key>
```

- The JWT must belong to an account with `ADMIN` or `DOCTOR` role (class-level `@PreAuthorize` on `IntegrationController`).
- The API key is validated against `app.integration.api-key` (dev default: `dev-integration-key`, from `application-h2.yml`). Requests without a matching key are rejected with 403.
- In development, obtain a token via `POST /api/v1/auth/login` using the default `doctor1` account, then send it alongside the key.

---

## ADT Channel

### Data Model

**Request body fields (mapped from HL7 PID/PV1 segments):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sourceMessageId` | string | Yes | Source message ID (MSH-10) |
| `eventType` | string | Yes | ADT event type, e.g., A01/A03/A08 |
| `eventTime` | string | No | Event timestamp (MSH-7) |
| `patient.mrn` | string | Yes | Patient medical record number (PID-3) |
| `patient.name` | string | No | Patient full name |
| `patient.dateOfBirth` | string | No | Date of birth |
| `patient.sexAtBirth` | string | No | Sex (M/F/U) |
| `patient.address.line1` | string | No | Address line 1 |
| `patient.address.city` | string | No | City |
| `patient.address.state` | string | No | State/province |
| `patient.address.zip` | string | No | ZIP/postal code |
| `visit.visitNumber` | string | No | Visit number (PV1-19) |
| `visit.admitDate` | string | No | Admission date (PV1-44) |
| `visit.dischargeDate` | string | No | Discharge date (PV1-45) |
| `visit.department` | string | No | Department (PV1-3) |
| `visit.admittingDoctorNpi` | string | No | Admitting doctor NPI (PV1-7) |

### Destination Connector Configuration

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

### Backend Processing Logic

- Looks up patient by `patient.mrn`: merges fields on update if found, creates a new Patient record if not.
- Sensitive fields (name, address, etc.) are automatically encrypted at rest via `@Convert(converter = AesAttributeConverter.class)` (AES-256-GCM).
- Every write is audited: `AdtService.processAdt` / `LabResultService.processLabResults` carry `@Auditable` (`integration` module, `ADT_UPSERT` / `LAB_RESULTS`) — Mirth-sourced PHI changes appear in the 21 CFR Part 11 audit trail.

---

## Lab Results Channel

### Data Model

**Request body fields (mapped from HL7 ORU^R01 OBR/OBX segments):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sourceMessageId` | string | Yes | Source message ID, used for idempotent dedup |
| `patientMrn` | string | Yes | Patient MRN |
| `orderCode` | string | No | Order code (OBR-4) |
| `collectionDate` | string | No | Collection date (OBR-7) |
| `results` | array | Yes | Result list, at least 1 item |
| `results[].loincCode` | string | Yes | LOINC code (OBX-3.1) |
| `results[].display` | string | No | LOINC display name (OBX-3.2) |
| `results[].value` | string | No | Result value (OBX-5) |
| `results[].unit` | string | No | Unit (OBX-6) |
| `results[].referenceRange` | string | No | Reference range (OBX-7) |
| `results[].abnormalFlag` | string | No | Abnormal flag: L=low H=high N=normal A=abnormal |

### Destination Connector Configuration

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

### Idempotent Dedup

The backend deduplicates by `sourceMessageId`: already-processed messages return `recordsCreated: 0`, preventing duplicate Observation rows. Mirth Connect can safely retry failed deliveries.

### Response Format

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

## Retry & Fault Tolerance

| Setting | Recommended | Notes |
|---------|------------|-------|
| Queuing | On (DISPATCHED) | Failed deliveries enqueue, no message loss |
| Retry Interval | 30s × 3, then 5min | Dense retries first, then back off |
| Rotate Queue | On | Prevents single queue file bloat |
| Error Response | Retry on 4xx/5xx | HTTP Sender default behavior |

Check ACK in the Response Transformer:

```javascript
var response = JSON.parse(connectorMessage.getResponseData());
if (response.data && response.data.status === 'ACK') {
    // Mark as successful, remove from queue
    return;
}
// Otherwise Mirth Connect handles per retry policy
```

---

## Mirth Connect Version Compatibility

- Targets **NextGen Connect 3.x** (formerly Mirth Connect 3.x) with the Rhino JS engine.
- For NextGen Connect 4.x (Nashorn → GraalVM JS), the Transformer code above is compatible, but ensure the `SerializerFactory` and HL7 extension are installed.
