import { useState, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { getEmergencyHistory, reviewEmergencyAccess } from '../../api/emergency'
import styles from '../shared.module.css'

export default function EmergencyAudit() {
  const location = useLocation()
  const [data, setData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [reloading, setReloading] = useState(false)
  const [audited, setAudited] = useState<string>('')
  const [patientIdFilter, setPatientIdFilter] = useState('')

  const fetchData = async (reloadId?: number) => {
    if (reloadId != null) setReloading(true); else setLoading(true)
    try {
      const params: any = {}
      if (audited !== '') params.audited = Number(audited)
      if (patientIdFilter !== '') params.patientId = Number(patientIdFilter)
      const r = await getEmergencyHistory(params)
      setData(r)
    } catch { setData([]) } finally {
      setLoading(false); setReloading(false)
    }
  }

  useEffect(() => { fetchData() }, [location])

  const handleReview = async (id: number) => {
    try {
      await reviewEmergencyAccess(id)
      fetchData()
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Review failed')
    }
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
        <button className={styles.btnPrimary} onClick={() => fetchData()} disabled={loading}>
          {loading ? '...' : 'Search'}
        </button>
      </div>

      <table className={styles.table}>
        <thead><tr>
          <th>ID</th><th>User ID</th><th>Patient ID</th><th>Reason</th><th>Accessed At</th><th>Expires At</th><th>Audited</th><th>Reviewed By</th><th>Reviewed At</th><th></th>
        </tr></thead>
        <tbody>
          {data.map((row: any) => (
            <tr key={row.id} className={styles.clickableRow}>
              <td>{row.id}</td>
              <td>{row.userId}</td>
              <td>{row.patientId}</td>
              <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.reason}</td>
              <td>{row.accessedAt}</td>
              <td>{row.expiresAt}</td>
              <td><span style={{ color: row.audited ? '#67c23a' : '#e6a23c', fontWeight: 600 }}>{row.audited ? 'Yes' : 'No'}</span></td>
              <td>{row.reviewedBy ?? '-'}</td>
              <td>{row.reviewedAt ?? '-'}</td>
              <td>
                <button className={styles.btnSm} disabled={row.audited} onClick={() => handleReview(row.id)}>Review</button>
              </td>
            </tr>
          ))}
          {!loading && data.length === 0 && (
            <tr><td colSpan={10} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No records</td></tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
