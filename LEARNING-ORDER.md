# Medical Project — 文档学习顺序

> 按认知递进逻辑排列，从"项目是什么"到"怎么演进"。

---

## 推荐路径

```
README.md  →  API-LAYOUT.md  →  backend-architecture-explained.md
                                    ↓
                              medical-learning-guide.md
                                    ↓
                              CLAUDE.md  →  ROADMAP.md
```

---

## 逐文件说明

| 顺序 | 文件 | 时间 | 角色 | 关键收获 |
|------|------|------|------|---------|
| **1** | `README.md` | 5 min | 大门 | 项目是什么、怎么跑起来、有哪些模块。先 `./mvnw spring-boot:run -Dspring-boot.run.profiles=h2` 跑起来 |
| **2** | `API-LAYOUT.md` | 15 min | 外部契约 | 91 个端点全景，每个模块做什么。打开 `http://localhost:8080/doc.html` 对照 Swagger 看 |
| **3** | `backend-architecture-explained.md` | 45 min | 内部骨架 | 12 层架构逐层展开 — 加密怎么工作、审计怎么隔离、FHIR 怎么构建、CDS 怎么检查。对照代码看 |
| **4** | `medical-learning-guide.md` | 30 min | 领域翻译 | "传统后端的 age→dateOfBirth"、"国内身份证→美国 SSN+MRN"、"普通 CRUD→保险理赔状态机"。**重点读第 3 节（美国医疗数据模型）** |
| **5** | `CLAUDE.md` | 10 min | 规则约束 | 加依赖前看一下、写 DTO 前看一下、提交前看一下 |
| **6** | `ROADMAP.md` | 5 min | 演进全貌 | 从 HIPAA 三支柱到 9 个 Round 全部完成的完整演进路径 |

---

## 速成路径（时间有限时）

```
README.md  →  API-LAYOUT.md  →  medical-learning-guide.md

（20 min）
```

这 3 个文件覆盖面试最常问的内容：
- 项目能做什么（README）
- 对外接口长什么样（API-LAYOUT）
- 为什么字段和国内系统完全不同（learning-guide 第 3 节）

---

## 按学习目标选择

| 你的目标 | 优先读 |
|---------|--------|
| 快速了解项目跑起来 | README |
| 前端对接接口 | API-LAYOUT |
| 理解后端架构设计 | architecture-explained |
| 国内后端转美国医疗 | learning-guide（全部） |
| 理解美式数据模型 | learning-guide 第 3 节 |
| 理解 HIPAA 合规落地 | architecture-explained 第 5–7 层 |
| 理解 FHIR 互操作 | architecture-explained 第 9 层 + learning-guide 第 4 节 |
| 理解 CDS / ePrescribing | architecture-explained 第 10.6–10.9 节 |
| 理解密钥轮换和加密 | architecture-explained 第 6 层 |
| 贡献代码前 | CLAUDE.md |
| 理解项目演进历史 | ROADMAP |
