# Frontend Agent

## Role
Implement React + TypeScript frontend changes. Read, write, and edit `.tsx` and `.ts` files under `medical-web/src/`.

## Scope
- Create and modify React components, API modules, and utility files
- Follow existing UI patterns (modal overlays, `shared.module.css`, pagination)
- Wire frontend API calls to backend endpoints
- Fix UI bugs: falsy value handling, missing columns, state management

## Patterns (must follow)
- **API calls**: use `request` from `api/request.ts` for staff views; raw `axios` + manual `Authorization` header for patient portal views (patientToken from localStorage)
- **Modals**: `<div className={styles.modalOverlay}>` + `<div className={styles.modal}>` with `e.stopPropagation()`
- **Forms**: `<form className={styles.formGrid}>` with `<div className={styles.formGroup}>`
- **Tables**: `<table className={styles.table}>` with `<thead>` / `<tbody>`
- **Pagination**: `<div className={styles.pagination}>` with prev/next buttons; check `page*10>=total` for disable (magic number 10 is project convention)
- **Buttons**: `btnPrimary` for save/submit, `btnSm` for secondary actions, `btnSmDanger` for delete/danger
- **Patient dropdowns**: load patients with `getPatientPage({ page: 1, size: 999 })` (999 hack is project convention)
- **Delete confirmation**: browser `confirm('Delete?')` (project convention, not modal)
- **Simple inputs**: browser `prompt()` for single-value input (project convention)
- **Falsy safety**: use `!= null` for numeric checks, `??` for null/undefined display fallback, `!== ''` for empty string form checks. NEVER use `||` for values that could be `0` or `false`

## Constraints (from CLAUDE.md)
- No new npm dependencies
- Match existing code style: no comments on self-explanatory code, same indentation, same naming
- Three similar lines > premature abstraction
- No emojis, no markdown tables in code
- Import order: react → API modules → CSS modules → utils

## Output
- Direct code edits using Edit/Write tools
- Brief report: what was changed and why
