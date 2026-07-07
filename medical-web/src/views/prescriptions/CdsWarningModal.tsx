import { useState } from 'react'
import styles from '../shared.module.css'

const severityColors: Record<string, string> = {
  contraindicated: '#F56C6C',
  severe: '#E6A23C',
  moderate: '#E6A23C',
  minor: '#909399'
}

const typeLabels: Record<string, string> = {
  DRUG_DRUG: 'Drug Interaction',
  DRUG_ALLERGY: 'Allergy Warning'
}

interface Warning {
  type: string
  severity: string
  drugsInvolved: string
  description: string
  recommendation: string
}

interface Props {
  warnings: Warning[]
  onOverride: () => void
  onCancel: () => void
}

export default function CdsWarningModal({ warnings, onOverride, onCancel }: Props) {
  const [confirmed, setConfirmed] = useState(false)

  return (
    <div className={styles.modalOverlay} onClick={onCancel}>
      <div className={styles.modal} onClick={e => e.stopPropagation()} style={{ maxWidth: 700 }}>
        <h3 style={{ color: '#E6A23C', marginBottom: 16 }}>
          Clinical Decision Support Alerts ({warnings.length} warning{warnings.length !== 1 ? 's' : ''})
        </h3>

        <div style={{ maxHeight: 400, overflowY: 'auto', marginBottom: 16 }}>
          {warnings.map((w, i) => {
            const color = severityColors[w.severity] ?? '#909399'
            const label = typeLabels[w.type] ?? w.type
            return (
              <div key={i} style={{ padding: '12px 16px', marginBottom: 8, border: '1px solid #ebeef5', borderRadius: 6, background: '#fafafa' }}>
                <div style={{ display: 'flex', gap: 8, marginBottom: 6, alignItems: 'center' }}>
                  <span style={{
                    display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600,
                    background: `${color}20`, color
                  }}>
                    {w.severity.toUpperCase()}
                  </span>
                  <span style={{ fontSize: 12, color: '#909399' }}>{label}</span>
                </div>
                <div style={{ fontSize: 13, marginBottom: 4 }}>
                  <strong>Drugs:</strong> {w.drugsInvolved}
                </div>
                <div style={{ fontSize: 13, marginBottom: 4, color: '#606266' }}>{w.description}</div>
                <div style={{ fontSize: 12, fontStyle: 'italic', color: '#909399' }}>{w.recommendation}</div>
              </div>
            )
          })}
        </div>

        <div style={{
          padding: '12px 16px', marginBottom: 16, border: '1px solid #fbc4c4',
          borderRadius: 6, background: '#fef0f0', fontSize: 13, color: '#F56C6C'
        }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
            <input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} />
            I understand the risks and want to override these warnings
          </label>
        </div>

        <div className={styles.formActions}>
          <button className={styles.btnSm} onClick={onCancel}>Cancel</button>
          <button
            className={styles.btnSmDanger}
            style={{ opacity: confirmed ? 1 : 0.5, fontWeight: 600 }}
            disabled={!confirmed}
            onClick={onOverride}
          >
            Override & Save
          </button>
        </div>
      </div>
    </div>
  )
}
