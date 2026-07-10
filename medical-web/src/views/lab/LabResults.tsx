import { useState, useEffect } from 'react'
import { getPatientPage } from '../../api/patient'
import { getObservations } from '../../api/observation'
import styles from '../shared.module.css'

const FLAG_COLOR: Record<string, string> = {
  N: '#67C23A', H: '#E6A23C', L: '#E6A23C', HH: '#F56C6C', LL: '#F56C6C', A: '#F56C6C',
}

export default function LabResults() {
  const [patients, setPatients] = useState<any[]>([])
  const [patientId, setPatientId] = useState<number | null>(null)
  const [observations, setObservations] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    getPatientPage({ page: 1, size: 999 }).then(r => setPatients(r.records || []))
  }, [])

  const loadObservations = async (pid: number) => {
    setPatientId(pid)
    setLoading(true)
    try {
      const r = await getObservations(pid)
      setObservations(r || [])
    } catch {
      setObservations([])
    } finally {
      setLoading(false)
    }
  }

  const grouped: Record<string, any[]> = {}
  observations.forEach(o => {
    const date = o.effectiveDate ? o.effectiveDate.substring(0, 16) : 'Unknown'
    if (!grouped[date]) grouped[date] = []
    grouped[date].push(o)
  })

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Lab Results</h2>
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'center' }}>
        <label style={{ fontSize: 13, color: '#606266' }}>Patient:</label>
        <select value={patientId ?? ''} onChange={e => { const v = e.target.value; if (v) loadObservations(Number(v)) }}
          style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
          <option value="">-- Select Patient --</option>
          {patients.map(p => <option key={p.id} value={p.id}>{p.name} (MRN: {p.mrn})</option>)}
        </select>
      </div>

      {loading && <p style={{ color: '#909399', fontSize: 13 }}>Loading...</p>}

      {!loading && patientId && observations.length === 0 && (
        <p style={{ color: '#909399', fontSize: 13 }}>No lab results found for this patient.</p>
      )}

      {!loading && observations.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Collection Date</th><th>Test</th><th>Value</th><th>Unit</th><th>Reference Range</th><th>Flag</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(grouped).map(([date, obs]: [string, any[]]) => {
              const firstRow = true
              return obs.map((o, i) => (
                <tr key={o.id}>
                  {i === 0 && <td rowSpan={obs.length} style={{ verticalAlign: 'top', fontWeight: 600 }}>{date}</td>}
                  <td>{o.loincDisplay || o.loincCode}</td>
                  <td>{o.obsValue}</td>
                  <td>{o.unit || '-'}</td>
                  <td>{o.referenceRange || '-'}</td>
                  <td>
                    {o.abnormalFlag && o.abnormalFlag !== 'N' ? (
                      <span style={{ color: FLAG_COLOR[o.abnormalFlag] || '#909399', fontWeight: 600 }}>
                        {o.abnormalFlag}
                      </span>
                    ) : (
                      <span style={{ color: '#67C23A' }}>N</span>
                    )}
                  </td>
                </tr>
              ))
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
