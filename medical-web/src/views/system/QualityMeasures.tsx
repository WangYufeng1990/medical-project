import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getMeasures, getMeasureReport, calculateMeasureReport, getMeasureHistory } from '../../api/quality'
import styles from '../shared.module.css'

export default function QualityMeasures() {
  const queryClient = useQueryClient()
  const [selectedCmsId, setSelectedCmsId] = useState<string | null>(null)

  const { data: measures, isLoading } = useQuery({
    queryKey: ['quality', 'measures'],
    queryFn: () => getMeasures(),
  })

  const { data: report, isFetching: reportLoading } = useQuery({
    queryKey: ['quality', 'report', selectedCmsId],
    queryFn: () => getMeasureReport(selectedCmsId!),
    enabled: selectedCmsId != null,
  })

  const { data: history } = useQuery({
    queryKey: ['quality', 'history', selectedCmsId],
    queryFn: () => getMeasureHistory(selectedCmsId!),
    enabled: selectedCmsId != null,
  })

  const calculateMutation = useMutation({
    mutationFn: (cmsId: string) => calculateMeasureReport(cmsId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quality', 'report', selectedCmsId] })
      queryClient.invalidateQueries({ queryKey: ['quality', 'history', selectedCmsId] })
    },
  })

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>eCQM Quality Measures</h2>

      <div style={{ display: 'grid', gridTemplateColumns: selectedCmsId ? '1fr 1fr' : '1fr', gap: 24 }}>
        <div>
          <table className={styles.table}>
            <thead><tr><th>CMS ID</th><th>Title</th><th>Period (months)</th></tr></thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={3} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>Loading...</td></tr>
              ) : (
                (measures ?? []).map((m: any) => (
                  <tr key={m.cmsId}
                    className={styles.clickableRow}
                    onClick={() => setSelectedCmsId(selectedCmsId === m.cmsId ? null : m.cmsId)}
                    style={{ background: selectedCmsId === m.cmsId ? '#ecf5ff' : undefined }}>
                    <td style={{ fontWeight: 600 }}>{m.cmsId}</td>
                    <td>{m.title}</td>
                    <td>{m.reportPeriodMonths}</td>
                  </tr>
                ))
              )}
              {!isLoading && (measures ?? []).length === 0 && (
                <tr><td colSpan={3} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No measures defined</td></tr>
              )}
            </tbody>
          </table>
        </div>

        {selectedCmsId && (
          <div style={{ background: '#fff', borderRadius: 8, padding: 24, boxShadow: '0 1px 6px rgba(0,0,0,.06)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
              <div>
                <h3 style={{ marginBottom: 4 }}>{report?.title ?? selectedCmsId}</h3>
                <p style={{ fontSize: 12, color: '#909399', margin: 0 }}>
                  Reporting period: {report?.reportPeriodMonths ?? '-'} months
                  {report?.calculatedAt && (
                    <span> · Last calculated: {report.calculatedAt.replace('T', ' ').substring(0, 19)}</span>
                  )}
                </p>
              </div>
              <button
                className={styles.btnPrimary}
                disabled={calculateMutation.isPending}
                onClick={() => calculateMutation.mutate(selectedCmsId)}
                style={{ whiteSpace: 'nowrap' }}>
                {calculateMutation.isPending ? 'Calculating...' : 'Calculate Now'}
              </button>
            </div>

            {reportLoading ? (
              <div style={{ color: '#909399', padding: 20, textAlign: 'center' }}>Loading...</div>
            ) : report ? (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
                  <MetricCard label="Denominator" value={report.denominator} color="#409EFF" />
                  <MetricCard label="Exclusions" value={report.exclusions} color="#E6A23C" />
                  <MetricCard label="Eligible Denominator" value={report.eligibleDenominator} color="#909399" />
                  <MetricCard label="Numerator" value={report.numerator} color="#67C23A" />
                </div>

                <div style={{ borderTop: '1px solid #ebeef5', paddingTop: 16 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
                    <span style={{ fontSize: 14, color: '#606266' }}>Performance Rate</span>
                    <span style={{ fontSize: 28, fontWeight: 700, color: performanceColor(report.performanceRate) }}>
                      {report.performanceRate}%
                    </span>
                  </div>
                  <div style={{ background: '#f5f7fa', borderRadius: 4, height: 12, overflow: 'hidden' }}>
                    <div style={{
                      width: `${Math.min(report.performanceRate, 100)}%`,
                      height: '100%',
                      background: performanceColor(report.performanceRate),
                      borderRadius: 4,
                      transition: 'width 0.5s ease',
                    }} />
                  </div>
                  <p style={{ fontSize: 12, color: '#909399', marginTop: 16, lineHeight: 1.6 }}>
                    {report.performanceTarget}
                  </p>
                </div>

                {(history ?? []).length > 0 && (
                  <div style={{ borderTop: '1px solid #ebeef5', paddingTop: 16, marginTop: 16 }}>
                    <h4 style={{ marginBottom: 8, fontSize: 13, color: '#606266' }}>Calculation History</h4>
                    <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                      <thead>
                        <tr style={{ borderBottom: '1px solid #ebeef5' }}>
                          <th style={{ textAlign: 'left', padding: '4px 8px', color: '#909399' }}>Time</th>
                          <th style={{ textAlign: 'right', padding: '4px 8px', color: '#909399' }}>Denom</th>
                          <th style={{ textAlign: 'right', padding: '4px 8px', color: '#909399' }}>Excl</th>
                          <th style={{ textAlign: 'right', padding: '4px 8px', color: '#909399' }}>Num</th>
                          <th style={{ textAlign: 'right', padding: '4px 8px', color: '#909399' }}>Rate</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(history ?? []).map((h: any) => (
                          <tr key={h.id} style={{ borderBottom: '1px solid #f5f5f5' }}>
                            <td style={{ padding: '4px 8px', color: '#606266' }}>{h.calculatedAt?.replace('T', ' ').substring(0, 19)}</td>
                            <td style={{ padding: '4px 8px', textAlign: 'right' }}>{h.denominator}</td>
                            <td style={{ padding: '4px 8px', textAlign: 'right' }}>{h.exclusions}</td>
                            <td style={{ padding: '4px 8px', textAlign: 'right' }}>{h.numerator}</td>
                            <td style={{ padding: '4px 8px', textAlign: 'right', fontWeight: 600, color: performanceColor(h.performanceRate) }}>{h.performanceRate}%</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </>
            ) : (
              <div style={{ color: '#909399', padding: 20, textAlign: 'center' }}>No data yet. Click "Calculate Now" to run the first calculation.</div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function MetricCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={{ background: '#fafafa', borderRadius: 6, padding: '12px 16px', borderLeft: `3px solid ${color}` }}>
      <div style={{ fontSize: 11, color: '#909399', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 700, color }}>{value}</div>
    </div>
  )
}

function performanceColor(rate: number): string {
  if (rate >= 70) return '#67C23A'
  if (rate >= 50) return '#E6A23C'
  return '#F56C6C'
}
