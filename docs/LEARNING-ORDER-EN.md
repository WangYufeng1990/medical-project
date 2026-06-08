# Medical Project — Document Learning Order

> Arranged by cognitive progression, from "what is this project" to "how it evolved."

---

## Recommended Path

```
README.md  →  API-LAYOUT-EN.md  →  backend-architecture-explained-EN.md
                                    ↓
                              medical-learning-guide-EN.md
                                    ↓
                              CLAUDE.md  →  ROADMAP-EN.md
```

---

## File-by-File Guide

| Order | File | Time | Role | Key Takeaway |
|-------|------|------|------|--------------|
| **1** | `README.md` | 5 min | Front door | What the project does, how to run it, module overview. Start: `./mvnw spring-boot:run -Dspring-boot.run.profiles=h2` |
| **2** | `API-LAYOUT-EN.md` | 15 min | External contract | Full API endpoint inventory, what each module does. Open Swagger UI at `/doc.html` side by side |
| **3** | `backend-architecture-explained-EN.md` | 45 min | Internal skeleton | 12-layer architecture + 5 advanced modules (CDS/Integration/LOINC/eRx/eCQM) |
| **4** | `medical-learning-guide-EN.md` | 30 min | Domain translation | "Traditional backend age→dateOfBirth", "Chinese national ID→US SSN+MRN". **Focus on Section 3 (US Medical Data Model)** |
| **5** | `CLAUDE.md` | 10 min | Rules & constraints | Read before adding dependencies, writing DTOs, or committing |
| **6** | `ROADMAP-EN.md` | 5 min | Evolution overview | From HIPAA three-pillar foundation through 9 Rounds + compliance + frontend migration |

---

## Speed-Run Path (When Time Is Tight)

```
README.md  →  API-LAYOUT-EN.md  →  medical-learning-guide-EN.md

(20 min)
```

These 3 files cover the most common interview topics:
- What the project does (README)
- What the external API looks like (API-LAYOUT)
- Why the data model is completely different from non-US systems (learning-guide Section 3)

---

## By Learning Goal

| Your Goal | Start With |
|-----------|-----------|
| Quick overview + run locally | README |
| Frontend integration / API contract | API-LAYOUT-EN |
| Backend architecture deep dive | architecture-explained-EN |
| Transitioning from non-US backend to US healthcare | learning-guide-EN (all sections) |
| Understanding the US medical data model | learning-guide-EN Section 3 |
| Understanding HIPAA compliance implementation | architecture-explained-EN Layers 5–7 |
| Understanding FHIR interoperability | architecture-explained-EN Layer 9 + learning-guide-EN Section 4 |
| Understanding CDS / ePrescribing | architecture-explained-EN §10.6–10.9 |
| Understanding key rotation & encryption | architecture-explained-EN Layer 6 |
| Before contributing code | CLAUDE.md |
| Understanding project evolution history | ROADMAP-EN |
