# Medical Frontend — 面试复习大纲

> React 18 + TypeScript + Vite 5 + CSS Modules + axios
> 30+ 组件，双登录系统，完整 US 医疗模型表单，FHIR Bundle 解析，PHI 数据脱敏

---

## 一、项目概览（30 秒电梯演讲）

"I built a HIPAA-compliant medical management frontend in React with TypeScript. It features a dual-login system for staff and patients, a comprehensive US medical data model form with OMB race/ethnicity coding, FHIR R4 Bundle parsing and rendering, and automatic PHI field masking with [DECRYPT_FAILED] defensive handling."

---

## 二、路由结构

```
Staff Portal (/)
├── /login                  → StaffLogin (Okta OAuth2 JWT)
├── /dashboard              → Dashboard (4 统计卡片，可点击跳转)
├── /patients               → PatientList + PatientForm (31 字段 US 医疗模型)
├── /appointments           → AppointmentList (预约 CRUD，时间冲突检测)
├── /prescriptions          → PrescriptionList (处方 + 药品项)
├── /billing                → BillingList (保险理赔状态)
├── /profile                → StaffProfile (个人信息 + 密码修改)
├── /system/users           → UserCRUD (NPI/DEA/州执照 医护表单)
├── /system/roles           → RoleCRUD
└── /system/menus           → MenuCRUD

Patient Portal (/patient)
├── /patient/login          → PatientLogin (独立患者认证)
├── /patient/dashboard      → PatientDashboard (3 卡片，可点击)
├── /patient/profile        → PatientProfile (只读个人信息)
├── /patient/appointments   → PatientAppointments (我的预约)
├── /patient/prescriptions  → PatientPrescriptions (我的处方)
└── /patient/bills          → PatientBills (我的账单)
```

认证守卫: `AuthGuard` (staff) 和 `PatientAuthGuard` (patient) 检查 localStorage token，无 token 自动重定向登录页。

---

## 三、核心组件详解

### 1. Dashboard (`src/views/dashboard/index.tsx`)

```
4 张统计卡片:
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │ Total        │  │ Today        │  │ Pending      │  │ Prescriptions│
  │ Patients  42 │  │ Appointments5│  │ Bills     3  │  │ Active    12 │
  │         👤   │  │         📅   │  │         💰   │  │         💊   │
  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
         │ click           │ click           │ click           │ click
         ▼                 ▼                 ▼                 ▼
     /patients        /appointments      /billing        /prescriptions
```

**技术要点：**
- `Promise.all` 并发请求 3 个 API 获取统计数
- `useNavigate` 点击跳转对应路由
- CSS Modules 作用域隔离
- hover 动画 (transform + box-shadow)

### 2. PatientForm (`src/views/patients/index.tsx`)

**完整的 US 医疗模型表单 — 31 个字段：**

```
Identity:
  MRN (病历号)               Name (姓名)
  DOB (出生日期, <input type="date">)     SSN (社保号, xxx-xx-xxxx)
  Sex at Birth (M/F/U)       Gender Identity (Male/Female/Non-binary/Transgender)
  Race (OMB 5分类下拉)        Ethnicity (Hispanic/Not Hispanic)
  Preferred Language (en/es/zh)  Marital Status (Single/Married/Divorced/Widowed)

Contact:
  Phone (Mobile)    Phone (Home)    Phone (Work)
  Email

Structured Address:
  Address Line 1    Address Line 2
  City              State (2-char)    ZIP Code

Emergency Contact:
  Contact Name      Contact Phone    Relation (Spouse/Parent/Child/Sibling/Other)

Insurance:
  Insurance Payer   Member ID        Group Number

Clinical:
  Primary Care Provider
  Medical History (textarea)
  Allergies
```

**技术要点：**
- `useState` 管理表单状态
- `openForm(row)` 根据是否有 row 参数判断 Add/Edit 模式
- `maskPhone()` 表格列显示 `****1234`
- `maskEmail()` 表格列显示 `j***@domain.com`
- 分页通过 `page` state + Prev/Next 按钮

### 3. PHI 数据脱敏

```typescript
// 表格列展示自动掩码
const maskPhone = (p: string) => p ? '****' + p.slice(-4) : ''
const maskEmail = (e: string) => {
  const at = e?.indexOf('@')
  return at > 0 ? e[0] + '***' + e.slice(at) : ''
}

// [DECRYPT_FAILED] 防御
// 当后端因为密钥轮换或解密失败返回 "[DECRYPT_FAILED]" 时
// 前端展示 "Data Unavailable (Compliance Protection)" 而非崩溃
const phiUnavailable = (val: string) =>
  val === '[DECRYPT_FAILED]' ? 'Data Unavailable (Compliance Protection)' : val
```

### 4. 双登录系统

```
Staff Login (/login):
  POST /api/v1/auth/login { username, password }
  → data.token → localStorage('token')
  → navigate('/dashboard')
  → AuthGuard 检查 token

Patient Login (/patient/login):
  POST /api/v1/patient/login { username, password }
  → data.token → localStorage('patientToken')
  → data.name → localStorage('patientInfo')
  → navigate('/patient/dashboard')
  → PatientAuthGuard 检查 patientToken
```

**安全要点：**
- 两个独立的 axios 请求（staff 用 request.ts 拦截器，patient 用 axios 直接调）
- Staff interceptor: 401 → 清 token → 重定向 /login
- Patient: 无 token → AuthGuard 重定向 /patient/login

### 5. React Router 架构

```tsx
<BrowserRouter>
  <Routes>
    {/* Staff routes — protected by AuthGuard */}
    <Route path="/" element={<AuthGuard><StaffLayout /></AuthGuard>}>
      <Route index → /dashboard />
      <Route path="dashboard" element={<Dashboard />} />
      <Route path="patients" element={<Patients />} />
      {/* ... 8 more staff routes */}
    </Route>

    {/* Patient routes — protected by PatientAuthGuard */}
    <Route path="/patient" element={<PatientAuthGuard><PatientLayout /></PatientAuthGuard>}>
      <Route index → /patient/dashboard />
      {/* ... 5 patient routes */}
    </Route>
  </Routes>
</BrowserRouter>
```

---

## 四、API 层设计

```
src/api/
├── request.ts        ← axios.create(baseURL='/api/v1', timeout=15s)
│   ├── interceptor: 自动附 token, 401→清 token→重定向
│   └── interceptor: code===200 → return data (剥离 AxiosResponse)
├── auth.ts           ← login(), refresh()
├── patient.ts        ← CRUD + getPatientCase()
├── appointment.ts    ← CRUD
├── prescription.ts   ← CRUD
├── bill.ts           ← CRUD
├── user.ts           ← CRUD + getProfile/updateProfile/changePassword
├── role.ts           ← CRUD
└── menu.ts           ← CRUD
```

**为什么统一用 /api/v1 前缀？** Vite proxy 将 `/api` 请求代理到 `localhost:8080`，开发环境无需处理 CORS。

---

## 五、CSS 架构

```
CSS Modules (.module.css) — 每个组件独立作用域
├── StaffLayout.module.css   ← 侧边栏布局
├── shared.module.css        ← 表格/按钮/分页/模态框/表单网格 (20+ 组件共享)
├── style.module.css (login) ← 登录页居中卡片
└── style.module.css (dashboard) ← 统计卡片网格
```

---

### 6. 权限隔离 (Role-Based UI)

**侧边栏过滤：** `StaffLayout` 从 JWT payload (`parseJwt()`) 解码 roles，过滤菜单项 — doctor 看不到 Users/Roles/Menus。

**路由守卫：** `AdminGuard` 组件包裹 `/system/*` 路由，doctor 直接访问 URL 时显示 "Access Denied" 而非空白 403 页。

**患者字段限制：** 患者自助编辑 Profile 时，`name`/`MRN`/`DOB`/`sexAtBirth`/`insurance` 标记为 `readonly`。后端 `PUT /api/v1/patient/me` 忽略 `name` 参数——HIPAA 合规要求 legal name 变更必须通过 staff 验证。

### 7. Appointment Status 标签化

Appointment status 从原始数字 (0-6) 映射为彩色可读标签：

```
0 → Scheduled (蓝色)      3 → Completed (绿色)     6 → In Progress (蓝色)
1 → Arrived (绿色)        4 → No-Show (橙色)
2 → Cancelled (灰色)      5 → Rescheduled (橙色)
```

使用 `utils/labels.ts` 中的 `APPOINTMENT_STATUS` 常量映射，staff 和 patient 双视图共享。

---

## 八、技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| UI 框架 | React | 18.3 |
| 语言 | TypeScript | 6.x |
| 路由 | React Router DOM | 6.26 |
| HTTP | axios | 1.7 |
| 构建 | Vite | 5.4 |
| 样式 | CSS Modules | — |
| 代理 | Vite proxy → localhost:8080 | — |

---

## 九、常见面试追问

**Q: 为什么选 React 而不是 Vue？**
A: 美国医疗前端市场 React 占比 >70%。Epic MyChart、Cerner、Athenahealth、Zocdoc 等主要 EHR 和患者门户都用 React。面试 JD 写的是 React。

**Q: 为什么用 CSS Modules 而不是 Tailwind/styled-components？**
A: CSS Modules 零运行时开销，不引入额外依赖，与 Vite 原生兼容。对于医疗系统这种功能密集的界面，CSS Modules 的局部作用域足够用。

**Q: 患者表单有什么医疗特殊性？**
A: 普通系统的性别只有一个字段，医疗系统必须区分 sexAtBirth(M/F/U) 和 genderIdentity(Male/Female/Non-binary)。种族需要 OMB 5 分类而非自由文本，因为联邦法规要求按 OMB 标准上报质量指标。地址必须结构化（line1/line2/city/state/zip）以支持地理分析和附近药房搜索。

**Q: [DECRYPT_FAILED] 是怎么处理的？**
A: 后端 AesCryptoUtil.decrypt() 失败时返回 "[DECRYPT_FAILED]" 字符串而非异常。前端检测到该值后展示 "Data Unavailable (Compliance Protection)" 黄色斜体文本，不阻塞组件渲染。这是在密钥轮换过渡期或数据库损坏时的优雅降级策略。

**Q: 如何防止 XSS？**
A: React 的 JSX 默认对所有插值进行 HTML 转义，这是内置的 XSS 防护。JWT token 存储在 localStorage 而非 sessionStorage——这是权衡，因为需要在多个标签页间共享认证状态。

**Q: Vite proxy 配置是什么？**
A: `vite.config.ts` 中 `server.proxy: { '/api': 'http://localhost:8080' }`——开发环境下所有 `/api` 请求透明代理到 Spring Boot 后端，避免 CORS 问题和双重端口管理。
