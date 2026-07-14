import { useState, useEffect, Fragment } from 'react'
import { useLocation } from 'react-router-dom'
import { getAuditLogs, getDistinctValues } from '../../api/audit'
import { PAGE_SIZE } from '../../utils/labels'
import styles from '../shared.module.css'

export default function AuditLogs() {
  const location = useLocation()
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)

  const [modules, setModules] = useState<string[]>([])
  const [actions, setActions] = useState<string[]>([])
  const [moduleFilter, setModuleFilter] = useState('')
  const [actionFilter, setActionFilter] = useState('')
  const [userIdFilter, setUserIdFilter] = useState('')
  const [patientIdFilter, setPatientIdFilter] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [expandedRow, setExpandedRow] = useState<number | null>(null)

  useEffect(() => {
    getDistinctValues().then(r => {
      setModules(r.modules || [])
      setActions(r.actions || [])
    }).catch(() => {})
  }, [])

  const buildParams = (p: number) => {
    const params: any = { page: p, size: PAGE_SIZE }
    if (moduleFilter !== '') params.module = moduleFilter
    if (actionFilter !== '') params.action = actionFilter
    if (userIdFilter !== '') params.userId = Number(userIdFilter)
    if (patientIdFilter !== '') params.patientId = Number(patientIdFilter)
    if (fromDate !== '') params.fromDate = fromDate
    if (toDate !== '') params.toDate = toDate
    return params
  }

  const fetchData = async (p: number) => {
    setLoading(true)
    try {
      const r = await getAuditLogs(buildParams(p))
      setData(r.records)
      setTotal(r.total)
      setPage(p)
    } catch {
      setData([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchData(1) }, [location])

  const handleSearch = () => fetchData(1)

  const handlePrev = () => { if (page > 1) fetchData(page - 1) }
  const handleNext = () => { if (page * PAGE_SIZE < total) fetchData(page + 1) }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Audit Log Viewer</h2>

      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>Module</label>
          <select value={moduleFilter} onChange={e => setModuleFilter(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, width: 130 }}>
            <option value="">All</option>
            {modules.map(m => <option key={m} value={m}>{m}</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>Action</label>
          <select value={actionFilter} onChange={e => setActionFilter(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, width: 160 }}>
            <option value="">All</option>
            {actions.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>User ID</label>
          <input type="number" value={userIdFilter} onChange={e => setUserIdFilter(e.target.value)}
            placeholder="User ID"
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, width: 100 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>Patient ID</label>
          <input type="number" value={patientIdFilter} onChange={e => setPatientIdFilter(e.target.value)}
            placeholder="Patient ID"
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, width: 100 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>From</label>
          <input type="date" value={fromDate} onChange={e => setFromDate(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: 12, color: '#909399' }}>To</label>
          <input type="date" value={toDate} onChange={e => setToDate(e.target.value)}
            style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }} />
        </div>
        <button className={styles.btnPrimary} onClick={handleSearch} disabled={loading}>
          {loading ? '...' : 'Search'}
        </button>
      </div>

      <table className={styles.table}>
        <thead><tr>
          <th>ID</th><th>User</th><th>Username</th><th>Patient</th><th>Module</th><th>Action</th><th>Target</th><th>Detail</th><th>IP</th><th>Timestamp</th>
        </tr></thead>
        <tbody>
          {data.map((row: any) => (<Fragment key={row.id}>
            <tr className={styles.clickableRow} onClick={() => setExpandedRow(expandedRow === row.id ? null : row.id)}>
              <td>{row.id}</td>
              <td>{row.userId ?? '-'}</td>
              <td>{row.username ?? '-'}</td>
              <td>{row.patientId ?? '-'}</td>
              <td>{row.module}</td>
              <td>{row.action}</td>
              <td>{row.targetId ?? '-'}</td>
              <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.detail ?? '-'}</td>
              <td>{row.ip ?? '-'}</td>
              <td style={{ whiteSpace: 'nowrap' }}>{row.createTime}</td>
            </tr>
            {expandedRow === row.id && (
              <tr key={`${row.id}-detail`}>
                <td colSpan={10} style={{ padding: '12px 16px', background: '#fafafa', fontSize: 12, color: '#606266', whiteSpace: 'pre-wrap', borderBottom: '2px solid #409EFF' }}>
                  <strong>Detail:</strong> {row.detail || 'No detail available'}
                </td>
              </tr>
            )}
          </Fragment>))}
          {!loading && data.length === 0 && (
            <tr><td colSpan={10} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No audit logs found</td></tr>
          )}
        </tbody>
      </table>

      <div className={styles.pagination}>
        <span>Total: {total}</span>
        <button disabled={page <= 1} onClick={handlePrev}>Prev</button>
        <span>Page {page}</span>
        <button disabled={page * PAGE_SIZE >= total} onClick={handleNext}>Next</button>
      </div>
    </div>
  )
}
