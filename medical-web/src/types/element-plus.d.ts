// Element Plus template ref typing helpers
// formRef.value?.validate() and formRef.value?.resetFields() resolve to 'never' in strict mode
// This declaration provides minimal typing to prevent TS2339 errors
declare global {
  interface ImportMeta {
    readonly env: Record<string, string>
  }
}

export {}
