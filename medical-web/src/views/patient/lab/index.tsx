import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import styles from '../../shared.module.css'

const FLAG_COLOR: Record<string, string> = {
  N: '#67C23A', H: '#E6A23C', L: '#E6A23C', HH: '#F56C6C', LL: '#F56C6C', A: '#F56C6C',
}

const LAB_PAGE_SIZE = 20

export default function PatientLab() {
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')
  const [loincFilter, setLoincFilter] = useState('')
  const [page, setPage] = useState(1)

  const { data: catalog } = useQuery({
    queryKey: ['loinc', 'catalog'],
    queryFn: () => patientRequest.get('/loinc/catalog'),
  })

  // Table mode: server-side pagination. Trend mode: full history of one test.
  const { data: pageData, isLoading: pageLoading } = useQuery({
    queryKey: ['me', 'lab', 'list', { page }],
    queryFn: () => patientRequest.get('/patient/me/observations', { params: { page, size: LAB_PAGE_SIZE } }),
    enabled: !loincFilter,
  })
  const { data: trendData, isLoading: trendLoading } = useQuery({
    queryKey: ['me', 'lab', 'trend', { loinc: loincFilter }],
    queryFn: () => patientRequest.get('/patient/me/observations/trend', { params: { loinc: loincFilter } }),
    enabled: !!loincFilter,
  })
  const isLoading = loincFilter ? trendLoading : pageLoading
  const allObservations = (loincFilter ? trendData : pageData?.records) ?? []
  const total = pageData?.total ?? 0

  const grouped: Record<string, any[]> = {}
  allObservations.forEach(o => {
    const date = o.effectiveDate ? o.effectiveDate.substring(0, 16) : 'Unknown'
    if (!grouped[date]) grouped[date] = []
    grouped[date].push(o)
  })

  const dateEntries = Object.entries(grouped)
  const trendDirection = loincFilter && dateEntries.length >= 2
    ? (() => {
        const dates = Object.keys(grouped).sort()
        const first = parseFloat(grouped[dates[0]][0]?.obsValue)
        const last = parseFloat(grouped[dates[dates.length - 1]][0]?.obsValue)
        if (isNaN(first) || isNaN(last)) return null
        return last > first ? '↑' : last < first ? '↓' : '→'
      })()
    : null

  const handleLoincChange = (code: string) => {
    setLoincFilter(code)
    setPage(1)
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>{info.name || 'Patient'} — Lab Results</h2>

      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'center' }}>
        <label style={{ fontSize: 13, color: '#606266' }}>Filter by test:</label>
        <select value={loincFilter} onChange={e => handleLoincChange(e.target.value)}
          style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
          <option value="">All tests</option>
          {(catalog ?? []).map((c: any) => <option key={c.loincCode} value={c.loincCode}>{c.display}</option>)}
        </select>
      </div>

      {isLoading && <p style={{ color: '#909399', fontSize: 13 }}>Loading...</p>}

      {!isLoading && allObservations.length === 0 && (
        <p style={{ color: '#909399', fontSize: 13 }}>No lab results found.</p>
      )}

      {!isLoading && allObservations.length > 0 && (
        <>
          {loincFilter && trendDirection && dateEntries.length >= 2 && (
            <div style={{ marginBottom: 16, padding: 16, background: '#f0f9ff', borderRadius: 8, borderLeft: '3px solid #409EFF' }}>
              <div style={{ fontSize: 13, color: '#606266', marginBottom: 8 }}>
                Trend for <strong>{allObservations[0]?.loincDisplay ?? loincFilter}</strong>
              </div>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16 }}>
                {dateEntries.sort(([a], [b]) => a.localeCompare(b)).map(([date, obs]: [string, any[]]) => {
                  const val = parseFloat(obs[0]?.obsValue)
                  return (
                    <div key={date} style={{ textAlign: 'center' }}>
                      <div style={{ fontSize: 18, fontWeight: 700, color: isNaN(val) ? '#909399' : FLAG_COLOR[obs[0]?.abnormalFlag] || '#409EFF' }}>
                        {obs[0]?.obsValue}
                      </div>
                      <div style={{ fontSize: 10, color: '#909399' }}>{obs[0]?.unit ?? ''}</div>
                      <div style={{ fontSize: 10, color: '#909399', marginTop: 4 }}>{date?.substring(0, 10)}</div>
                    </div>
                  )
                })}
                <div style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 24, color: trendDirection === '↓' ? '#67C23A' : trendDirection === '↑' ? '#E6A23C' : '#909399' }}>
                    {trendDirection}
                  </div>
                </div>
              </div>
            </div>
          )}

          <table className={styles.table}>
            <thead>
              <tr>
                <th>Collection Date</th><th>Test</th><th>Value</th><th>Unit</th><th>Reference Range</th><th>Flag</th>
              </tr>
            </thead>
            <tbody>
              {dateEntries.sort(([a], [b]) => b.localeCompare(a)).map(([date, obs]: [string, any[]]) =>
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

          {!loincFilter && total > LAB_PAGE_SIZE && (
            <div className={styles.pagination}>
              <span>Total: {total}</span>
              <button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>Prev</button>
              <span>Page {page}</span>
              <button disabled={page * LAB_PAGE_SIZE >= total} onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
