import { createContext, useContext, useState, useCallback, ReactNode } from 'react'
import styles from '../views/shared.module.css'

interface DialogOptions {
  message: string
  type?: 'confirm' | 'prompt' | 'alert'
  placeholder?: string
  defaultValue?: string
}

interface DialogState extends DialogOptions {
  id: number
  resolve: (value: string | boolean) => void
}

const ConfirmContext = createContext<{
  confirm: (message: string) => Promise<boolean>
  prompt: (message: string, defaultValue?: string) => Promise<string | null>
  alert: (message: string) => Promise<void>
}>(null!)

export function useConfirm() {
  return useContext(ConfirmContext)
}

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [dialogs, setDialogs] = useState<DialogState[]>([])
  let idCounter = 0

  const open = useCallback((opts: DialogOptions) => {
    return new Promise<string | boolean>((resolve) => {
      setDialogs(prev => [...prev, { ...opts, id: ++idCounter, resolve }])
    })
  }, [])

  const confirm = useCallback((message: string) => open({ message, type: 'confirm' }).then(r => r === true), [open])
  const prompt = useCallback((message: string, defaultValue?: string) => open({ message, type: 'prompt', defaultValue }) as Promise<string | null>, [open])
  const alert = useCallback((message: string) => open({ message, type: 'alert' }).then(() => {}), [open])

  const close = (id: number, result: string | boolean) => {
    setDialogs(prev => prev.filter(d => d.id !== id))
    const dialog = dialogs.find(d => d.id === id)
    dialog?.resolve(result)
  }

  return (
    <ConfirmContext.Provider value={{ confirm, prompt, alert }}>
      {children}
      {dialogs.map(d => {
        if (d.type === 'prompt') {
          let value = d.defaultValue || ''
          return (
            <div key={d.id} className={styles.modalOverlay} onClick={() => close(d.id, null as any)}>
              <div className={styles.modal} onClick={e => e.stopPropagation()}>
                <h3>{d.message}</h3>
                <input autoFocus style={{ width: '100%', margin: '12px 0', padding: '8px', border: '1px solid #dcdfe6', borderRadius: 4 }} defaultValue={d.defaultValue} onChange={e => value = e.target.value} onKeyDown={e => { if (e.key === 'Enter') close(d.id, value) }} />
                <div className={styles.formActions} style={{ marginTop: 12 }}>
                  <button className={styles.btnSm} onClick={() => close(d.id, null as any)}>Cancel</button>
                  <button className={styles.btnPrimary} onClick={() => close(d.id, value)}>OK</button>
                </div>
              </div>
            </div>
          )
        }
        if (d.type === 'alert') {
          return (
            <div key={d.id} className={styles.modalOverlay} onClick={() => close(d.id, true)}>
              <div className={styles.modal} onClick={e => e.stopPropagation()}>
                <p style={{ margin: '0 0 16px' }}>{d.message}</p>
                <div className={styles.formActions}>
                  <button className={styles.btnPrimary} onClick={() => close(d.id, true)} autoFocus>OK</button>
                </div>
              </div>
            </div>
          )
        }
        return (
          <div key={d.id} className={styles.modalOverlay} onClick={() => close(d.id, false)}>
            <div className={styles.modal} onClick={e => e.stopPropagation()}>
              <p style={{ margin: '0 0 16px' }}>{d.message}</p>
              <div className={styles.formActions}>
                <button className={styles.btnSm} onClick={() => close(d.id, false)}>Cancel</button>
                <button className={styles.btnPrimary} onClick={() => close(d.id, true)} autoFocus>Confirm</button>
              </div>
            </div>
          </div>
        )
      })}
    </ConfirmContext.Provider>
  )
}
