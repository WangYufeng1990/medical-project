import { useState, useEffect } from 'react'
import axios from 'axios'
import styles from '../../shared.module.css'

const FLAG_COLOR: Record<string, string> = {
  N: '#67C23A', H: '#E6A23C', L: '#E6A23C', HH: '#F56C6C', LL: '#F56C6C', A: '#F56C6C',
}

export default function PatientLab() {
  const [observations, setObservations] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')

  useEffect(() => {
    axios.get('/api/v1/patient/me/observations', { headers })
      .then(r => setObservations(r.data.data || []))
      .catch(() => setObservations([]))
      .finally(() => setLoading(false))
  }, [])

  const grouped: Record<string, any[]> = {}
  observations.forEach(o => {
    const date = o.effectiveDate ? o.effectiveDate.substring(0, 16) : 'Unknown'
    if (!grouped[date]) grouped[date] = []
    grouped[date].push(o)
  })

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>{info.name || 'Patient'} — Lab Results</h2>

      {loading && <p style={{ color: '#909399', fontSize: 13 }}>Loading...</p>}

      {!loading && observations.length === 0 && (
        <p style={{ color: '#909399', fontSize: 13 }}>No lab results found.</p>
      )}

      {!loading && observations.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Collection Date</th><th>Test</th><th>Value</th><th>Unit</th><th>Reference Range</th><th>Flag</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(grouped).map(([date, obs]: [string, any[]]) =>
              obs.map((o, i) => (
                <tr key={o.id}>
                  {i === 0 && <td rowSpan={obs.length} style={{ verticalAlign: 'top', fontWeight: 600 }}>{date}</td>}
                  <td>{o.loincDisplay || o.loincCode}</td>
                  <td>{o.obsValue}</td>
                  <td>{o.unit || '-'}</td>
                  <td>{o.referenceRange || '-'}</td>
                  <td>
                    {o.abnormalFlag && o.abnormalFlag !== 'N' ? (
                      <span style={{ color: FLAG_COLOR[o.abnormalFlag] || '#909399', fontWeight: 600 }}>{o.abnormalFlag}</span>
                    ) : (
                      <span style={{ color: '#67C23A' }}>N</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}
