import { useState, useEffect } from 'react'
import axios from 'axios'
import styles from '../../shared.module.css'

export default function PatientLab() {
  const [catalog, setCatalog] = useState<any[]>([])
  const [selectedLoinc, setSelectedLoinc] = useState('')
  const [observations, setObservations] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  useEffect(() => {
    axios.get('/api/v1/loinc/catalog', { headers })
      .then(r => setCatalog(r.data.data || []))
      .catch(() => {})
  }, [])

  const handleTestSelect = (loinc: string) => {
    setSelectedLoinc(loinc)
    if (!loinc) { setObservations([]); return }
    setLoading(true)
    axios.get(`/api/v1/patient/me/observations?loinc=${loinc}`, { headers })
      .then(r => setObservations(r.data.data || []))
      .catch(() => setObservations([]))
      .finally(() => setLoading(false))
  }

  const flagColor = (f: string | null | undefined) => {
    if (f === 'N') return '#67C23A'
    if (f === 'L' || f === 'LL') return '#E6A23C'
    if (f === 'H' || f === 'HH') return '#f56c6c'
    return '#909399'
  }

  const flagLabel = (f: string | null | undefined) => {
    const map: Record<string, string> = { N: 'Normal', L: 'Low', LL: 'Crit Low', H: 'High', HH: 'Crit High' }
    return f ? (map[f] || f) : '-'
  }

  const finalObservations = observations.filter(
    (o: any) => o.status === 'final' || o.status === 'corrected' || !o.status
  )

  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>{info.name || 'Patient'} — Lab Results</h2>

      <div className={styles.formGroup} style={{ marginBottom: 20, maxWidth: 400 }}>
        <label>Test</label>
        <select value={selectedLoinc} onChange={e => handleTestSelect(e.target.value)}
          style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
          <option value="">-- Select Test --</option>
          {catalog.map((c: any) => (
            <option key={c.loincCode} value={c.loincCode}>{c.display} ({c.loincCode})</option>
          ))}
        </select>
      </div>

      {loading && <p style={{ color: '#909399', padding: 40, textAlign: 'center' }}>Loading...</p>}

      {!loading && selectedLoinc && finalObservations.length === 0 && (
        <p style={{ color: '#909399', padding: 40, textAlign: 'center' }}>No lab results found.</p>
      )}

      {!selectedLoinc && (
        <p style={{ color: '#909399', padding: 40, textAlign: 'center' }}>Select a test to view your lab results.</p>
      )}

      {finalObservations.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Date</th>
              <th>Test</th>
              <th>Value</th>
              <th>Unit</th>
              <th>Ref Range</th>
              <th>Flag</th>
            </tr>
          </thead>
          <tbody>
            {finalObservations.map((o: any, idx: number) => (
              <tr key={o.id} style={idx === 0 ? { background: '#f0f9ff', fontWeight: 600 } : {}}>
                <td>{o.effectiveDate ? o.effectiveDate.substring(0, 10) : '-'}</td>
                <td>{o.loincDisplay || o.loincCode}</td>
                <td>{o.obsValue ?? '-'}</td>
                <td>{o.unit || '-'}</td>
                <td>{o.referenceRange || '-'}</td>
                <td style={{ color: flagColor(o.abnormalFlag), fontWeight: 600 }}>
                  {flagLabel(o.abnormalFlag)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
