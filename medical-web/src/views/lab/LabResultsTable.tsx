import { ObservationVO } from '../../types/entities'
import styles from '../shared.module.css'
import { ABNORMAL_FLAG_COLOR, ABNORMAL_FLAG_LEGEND } from '../../utils/labels'

export const LAB_PAGE_SIZE = 20

export function LabFlagLegend() {
  return (
    <div style={{ marginLeft: 'auto', display: 'flex', gap: 10, fontSize: 11, color: '#909399', alignItems: 'center' }}>
      Flag: {ABNORMAL_FLAG_LEGEND.map((f, i) => (
        <span key={f.text} style={{ color: f.color, marginLeft: i ? 10 : 0 }}>{f.text}</span>
      ))}
    </div>
  )
}

type Props = {
  observations: ObservationVO[]
  isLoading: boolean
  /** A LOINC filter is active: render the trend banner instead of paging. */
  trendMode: boolean
  /** LOINC code, used when the display name is missing. */
  trendLabel: string
  page: number
  total: number
  onPageChange: (page: number) => void
  /** Empty string suppresses the message — the staff view has no patient selected yet. */
  emptyMessage: string
}

/**
 * Results grouped by collection date, with the trend banner above when a single
 * test is filtered. The staff and patient lab pages render the same data, so the
 * grouping, the trend calculation and the table live here once.
 */
export default function LabResultsTable({
  observations, isLoading, trendMode, trendLabel, page, total, onPageChange, emptyMessage,
}: Props) {
  const grouped: Record<string, ObservationVO[]> = {}
  observations.forEach(o => {
    const date = o.effectiveDate ? o.effectiveDate.substring(0, 16) : 'Unknown'
    if (!grouped[date]) grouped[date] = []
    grouped[date].push(o)
  })

  const dateEntries = Object.entries(grouped)
  const trendDirection = trendMode && dateEntries.length >= 2
    ? (() => {
        const dates = Object.keys(grouped).sort()
        const first = parseFloat(grouped[dates[0]][0]?.obsValue ?? '')
        const last = parseFloat(grouped[dates[dates.length - 1]][0]?.obsValue ?? '')
        if (isNaN(first) || isNaN(last)) return null
        return last > first ? '↑' : last < first ? '↓' : '→'
      })()
    : null

  if (isLoading) return <p style={{ color: '#909399', fontSize: 13 }}>Loading...</p>
  if (observations.length === 0) {
    return emptyMessage ? <p style={{ color: '#909399', fontSize: 13 }}>{emptyMessage}</p> : null
  }

  return (
    <>
      {trendMode && trendDirection && dateEntries.length >= 2 && (
        <div style={{ marginBottom: 16, padding: 16, background: '#f0f9ff', borderRadius: 8, borderLeft: '3px solid #409EFF' }}>
          <div style={{ fontSize: 13, color: '#606266', marginBottom: 8 }}>
            Trend for <strong>{observations[0]?.loincDisplay ?? trendLabel}</strong>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16 }}>
            {dateEntries.sort(([a], [b]) => a.localeCompare(b)).map(([date, obs]) => {
              const val = parseFloat(obs[0]?.obsValue ?? '')
              return (
                <div key={date} style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 18, fontWeight: 700, color: isNaN(val) ? '#909399' : ABNORMAL_FLAG_COLOR[obs[0]?.abnormalFlag || ''] || '#409EFF' }}>
                    {obs[0]?.obsValue}
                  </div>
                  <div style={{ fontSize: 10, color: '#909399' }}>{obs[0]?.unit ?? ''}</div>
                  <div style={{ fontSize: 10, color: '#909399', marginTop: 4 }}>{date?.substring(0, 10)}</div>
                </div>
              )
            })}
            <div style={{ textAlign: 'center' }}>
              <div role="img" aria-label={trendDirection === '↑' ? 'Trend increasing' : trendDirection === '↓' ? 'Trend decreasing' : 'Trend stable'}
                style={{ fontSize: 24, color: trendDirection === '↓' ? '#67C23A' : trendDirection === '↑' ? '#E6A23C' : '#909399' }}>
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
          {dateEntries.sort(([a], [b]) => b.localeCompare(a)).map(([date, obs]) =>
            obs.map((o, i) => (
              <tr key={o.id}>
                {i === 0 && <td rowSpan={obs.length} style={{ verticalAlign: 'top', fontWeight: 600 }}>{date}</td>}
                <td>{o.loincDisplay || o.loincCode}</td>
                <td>{o.obsValue}</td>
                <td>{o.unit || '-'}</td>
                <td>{o.referenceRange || '-'}</td>
                <td>
                  {o.abnormalFlag && o.abnormalFlag !== 'N' ? (
                    <span style={{ color: ABNORMAL_FLAG_COLOR[o.abnormalFlag] || '#909399', fontWeight: 600 }}>
                      {o.abnormalFlag}
                    </span>
                  ) : (
                    <span style={{ color: '#67C23A' }}>N</span>
                  )}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {!trendMode && total > LAB_PAGE_SIZE && (
        <div className={styles.pagination}>
          <span>Total: {total}</span>
          <button disabled={page <= 1} onClick={() => onPageChange(page - 1)}>Prev</button>
          <span>Page {page}</span>
          <button disabled={page * LAB_PAGE_SIZE >= total} onClick={() => onPageChange(page + 1)}>Next</button>
        </div>
      )}
    </>
  )
}
