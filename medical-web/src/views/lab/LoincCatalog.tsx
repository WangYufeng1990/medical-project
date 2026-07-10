import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getLoincCatalog } from '../../api/observation'
import styles from '../shared.module.css'

export default function LoincCatalog() {
  const navigate = useNavigate()
  const [catalog, setCatalog] = useState<any[]>([])
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})

  useEffect(() => {
    getLoincCatalog()
      .then(list => {
        setCatalog(list)
        const init: Record<string, boolean> = {}
        list.forEach((c: any) => {
          if (c.panelParentCode) init[c.panelParentCode] = true
        })
        setExpanded(init)
      })
      .catch(() => {})
  }, [])

  const panels = catalog.reduce((acc: Record<string, any[]>, c: any) => {
    const parent = c.panelParentCode || 'UNGROUPED'
    if (!acc[parent]) acc[parent] = []
    acc[parent].push(c)
    return acc
  }, {} as Record<string, any[]>)

  const panelLabels: Record<string, string> = {
    'LP14639-3': 'Basic Metabolic Panel',
    'LP38690-7': 'Complete Blood Count',
    'LP150074-2': 'Lipid Panel',
    'LP14297-1': 'Liver Function',
    'LP38743-4': 'Thyroid Panel',
    'LP150076-7': 'Hemoglobin A1c',
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>LOINC Catalog</h2>

      {Object.entries(panels).map(([parentCode, tests]) => (
        <div key={parentCode} style={{
          marginBottom: 16, background: '#fff', borderRadius: 6, boxShadow: '0 1px 6px rgba(0,0,0,.06)',
        }}>
          <div
            onClick={() => setExpanded(prev => ({ ...prev, [parentCode]: !prev[parentCode] }))}
            style={{
              padding: '14px 20px', cursor: 'pointer', fontWeight: 600, fontSize: 14,
              borderBottom: expanded[parentCode] ? '1px solid #ebeef5' : 'none',
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            }}
          >
            <span>{panelLabels[parentCode] || parentCode}</span>
            <span style={{ color: '#909399', fontSize: 12 }}>
              {tests.length} test{tests.length !== 1 ? 's' : ''} {expanded[parentCode] ? '[-]' : '[+]'}
            </span>
          </div>
          {expanded[parentCode] && (
            <table className={styles.table} style={{ boxShadow: 'none', borderRadius: 0 }}>
              <thead>
                <tr>
                  <th>LOINC Code</th>
                  <th>Display Name</th>
                  <th>Unit</th>
                  <th>Ref Range Low</th>
                  <th>Ref Range High</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {tests.map((t: any) => (
                  <tr key={t.id}>
                    <td style={{ fontFamily: 'monospace' }}>{t.loincCode}</td>
                    <td>{t.display}</td>
                    <td>{t.unit || '-'}</td>
                    <td>{t.refRangeLow || '-'}</td>
                    <td>{t.refRangeHigh || '-'}</td>
                    <td>
                      <button className={styles.btnSm} onClick={() => navigate(`/lab?loinc=${t.loincCode}`)}>
                        View Trend
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ))}
    </div>
  )
}
