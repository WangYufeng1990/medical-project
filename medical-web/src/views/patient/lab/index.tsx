import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import styles from '../../shared.module.css'

const FLAG_COLOR: Record<string, string> = {
  N: '#67C23A', H: '#E6A23C', L: '#E6A23C', HH: '#F56C6C', LL: '#F56C6C', A: '#F56C6C',
}

export default function PatientLab() {
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')
  const [loincFilter, setLoincFilter] = useState('')

  const { data: allData, isLoading } = useQuery({
    queryKey: ['me', 'lab'],
    queryFn: () => patientRequest.get('/patient/me/observations').then(r => r.data.data ?? []),
  })
  const allObservations = allData ?? []

  const observations = loincFilter
    ? allObservations.filter(o => o.loincCode === loincFilter)
    : allObservations

  const distinctLoincs = useMemo(() => {
    return [...new Set(allObservations.map(o => o.loincCode))]
      .map(code => {
        const o = allObservations.find(x => x.loincCode === code)
        return { code, display: o?.loincDisplay ?? code }
      })
      .sort((a, b) => a.display.localeCompare(b.display))
  }, [allObservations])

  const grouped: Record<string, any[]> = {}
  observations.forEach(o => {
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

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>{info.name || 'Patient'} — Lab Results</h2>

      {!isLoading && allObservations.length > 0 && (
        <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'center' }}>
          <label style={{ fontSize: 13, color: '#606266' }}>Filter by test:</label>
          <select value={loincFilter} onChange={e => setLoincFilter(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
            <option value="">All tests</option>
            {distinctLoincs.map(l => <option key={l.code} value={l.code}>{l.display}</option>)}
          </select>
        </div>
      )}

      {isLoading && <p style={{ color: '#909399', fontSize: 13 }}>Loading...</p>}

      {!isLoading && observations.length === 0 && (
        <p style={{ color: '#909399', fontSize: 13 }}>No lab results found.</p>
      )}

      {!isLoading && observations.length > 0 && (
        <>
          {loincFilter && trendDirection && dateEntries.length >= 2 && (
            <div style={{ marginBottom: 16, padding: 16, background: '#f0f9ff', borderRadius: 8, borderLeft: '3px solid #409EFF' }}>
              <div style={{ fontSize: 13, color: '#606266', marginBottom: 8 }}>
                Trend for <strong>{observations[0]?.loincDisplay ?? loincFilter}</strong>
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
        </>
      )}
    </div>
  )
}
