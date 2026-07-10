import { useState, useEffect } from 'react'
import { getPatientPage } from '../../api/patient'
import { getObservations, getLoincCatalog } from '../../api/observation'
import styles from '../shared.module.css'

export default function LabResults() {
  const [patients, setPatients] = useState<any[]>([])
  const [patientId, setPatientId] = useState<number | null>(null)
  const [catalog, setCatalog] = useState<any[]>([])
  const [panels, setPanels] = useState<{ code: string; display: string; count: number }[]>([])
  const [selectedPanel, setSelectedPanel] = useState('')
  const [panelTests, setPanelTests] = useState<any[]>([])
  const [selectedLoinc, setSelectedLoinc] = useState('')
  const [observations, setObservations] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    getPatientPage({ page: 1, size: 999 }).then(r => setPatients(r.records || [])).catch(() => {})
    getLoincCatalog().then(list => {
      setCatalog(list)
      const map: Record<string, { display: string; count: number }> = {}
      list.forEach((c: any) => {
        const parent = c.panelParentCode || 'UNGROUPED'
        map[parent] = map[parent] || { display: parent, count: 0 }
        map[parent].count++
      })
      setPanels(Object.entries(map).map(([code, v]) => ({
        code,
        display: code === 'UNGROUPED' ? 'Individual Tests' : code,
        count: v.count,
      })))
    }).catch(() => {})
  }, [])

  const handlePanelChange = (code: string) => {
    setSelectedPanel(code)
    setSelectedLoinc('')
    setObservations([])
    const tests = code
      ? catalog.filter((c: any) => c.panelParentCode === code)
      : []
    setPanelTests(tests)
  }

  const handleTestSelect = (loincCode: string) => {
    setSelectedLoinc(loincCode)
    if (patientId == null) return
    setLoading(true)
    getObservations(patientId, loincCode)
      .then(setObservations)
      .catch(() => setObservations([]))
      .finally(() => setLoading(false))
  }

  const handlePatientChange = (id: number | null) => {
    setPatientId(id)
    setObservations([])
    if (id != null && selectedLoinc) {
      setLoading(true)
      getObservations(id, selectedLoinc)
        .then(setObservations)
        .catch(() => setObservations([]))
        .finally(() => setLoading(false))
    }
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

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Lab Results</h2>

      <div style={{ display: 'flex', gap: 16, marginBottom: 20, flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div className={styles.formGroup}>
          <label>Patient</label>
          <select value={patientId ?? ''} onChange={e => handlePatientChange(e.target.value ? Number(e.target.value) : null)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, minWidth: 200 }}>
            <option value="">-- Select Patient --</option>
            {patients.map((p: any) => (
              <option key={p.id} value={p.id}>{p.name} (ID: {p.id})</option>
            ))}
          </select>
        </div>

        <div className={styles.formGroup}>
          <label>Panel</label>
          <select value={selectedPanel} onChange={e => handlePanelChange(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, minWidth: 200 }}>
            <option value="">-- Select Panel --</option>
            {panels.map(p => (
              <option key={p.code} value={p.code}>{p.display} ({p.count})</option>
            ))}
          </select>
        </div>

        <div className={styles.formGroup}>
          <label>Test</label>
          <select value={selectedLoinc} onChange={e => handleTestSelect(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, minWidth: 250 }}>
            <option value="">-- Select Test --</option>
            {panelTests.map((t: any) => (
              <option key={t.loincCode} value={t.loincCode}>{t.display} ({t.loincCode})</option>
            ))}
          </select>
        </div>
      </div>

      {patientId == null && (
        <p style={{ color: '#909399', padding: 40, textAlign: 'center' }}>
          Select a patient, panel, and test to view lab results.
        </p>
      )}

      {patientId != null && selectedLoinc && loading && (
        <p style={{ color: '#909399', padding: 40, textAlign: 'center' }}>Loading...</p>
      )}

      {patientId != null && selectedLoinc && !loading && finalObservations.length === 0 && (
        <p style={{ color: '#909399', padding: 40, textAlign: 'center' }}>No lab results found.</p>
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
