import { useState, Fragment } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getEmergencyHistory, reviewEmergencyAccess } from '../../api/emergency'
import styles from '../shared.module.css'

export default function EmergencyAudit() {
  const queryClient = useQueryClient()
  const [audited, setAudited] = useState('')
  const [patientIdFilter, setPatientIdFilter] = useState('')
  const [searchFilters, setSearchFilters] = useState<Record<string, any>>({})
  const [expandedRow, setExpandedRow] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['emergency', 'list', searchFilters],
    queryFn: () => {
      const params: any = {}
      if (searchFilters.audited !== undefined && searchFilters.audited !== '') params.audited = searchFilters.audited
      if (searchFilters.patientId) params.patientId = searchFilters.patientId
      return getEmergencyHistory(params)
    },
  })
  const list = data ?? []

  const reviewMutation = useMutation({
    mutationFn: (id: number) => reviewEmergencyAccess(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['emergency'] }),
    onError: () => alert('Review failed'),
  })

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  const handleSearch = () => {
    const filters: any = {}
    if (audited !== '') filters.audited = Number(audited)
    if (patientIdFilter !== '') filters.patientId = Number(patientIdFilter)
    setSearchFilters(filters)
  }

  const handleReview = (id: number) => {
    reviewMutation.mutate(id)
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Emergency Access Audit</h2>

      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'flex-end' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>Patient ID</label>
          <input value={patientIdFilter} onChange={e => setPatientIdFilter(e.target.value)}
            placeholder="Filter by patient ID"
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, width: 160 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>Audited</label>
          <select value={audited} onChange={e => setAudited(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
            <option value="">All</option>
            <option value="0">Unreviewed</option>
            <option value="1">Reviewed</option>
          </select>
        </div>
        <button className={styles.btnPrimary} onClick={handleSearch} disabled={isLoading}>
          {isLoading ? '...' : 'Search'}
        </button>
      </div>

      <table className={styles.table}>
        <thead><tr>
          <th>ID</th><th>User ID</th><th>Patient ID</th><th>Reason</th><th>Accessed At</th><th>Expires At</th><th>Audited</th><th>Reviewed By</th><th>Reviewed At</th><th></th>
        </tr></thead>
        <tbody>
          {list.map((row: any) => (<Fragment key={row.id}>
            <tr className={styles.clickableRow} onClick={() => setExpandedRow(expandedRow === row.id ? null : row.id)}>
              <td>{row.id}</td>
              <td>{row.userId}</td>
              <td>{row.patientId}</td>
              <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.reason}</td>
              <td>{row.accessedAt}</td>
              <td>{row.expiresAt}</td>
              <td><span style={{ color: row.audited ? '#67c23a' : '#e6a23c', fontWeight: 600 }}>{row.audited ? 'Yes' : 'No'}</span></td>
              <td>{row.reviewedBy ?? '-'}</td>
              <td>{row.reviewedAt ?? '-'}</td>
              <td onClick={e => e.stopPropagation()}>
                <button className={styles.btnSm} disabled={row.audited} onClick={() => handleReview(row.id)}>Review</button>
              </td>
            </tr>
            {expandedRow === row.id && (
              <tr key={`${row.id}-detail`}>
                <td colSpan={10} style={{ padding: '12px 16px', background: '#fafafa', fontSize: 12, color: '#606266', whiteSpace: 'pre-wrap', borderBottom: '2px solid #409EFF' }}>
                  <strong>Full Reason:</strong> {row.reason || 'No reason provided'}
                </td>
              </tr>
            )}
          </Fragment>))}
          {!isLoading && list.length === 0 && (
            <tr><td colSpan={10} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No records</td></tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
