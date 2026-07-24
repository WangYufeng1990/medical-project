import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { hasAnyRole, getUserRoles } from '../utils/auth'
import { logout } from '../api/auth'
import { downloadPatientsCsv, downloadBillsCsv } from '../api/export'
import { useState, useEffect } from 'react'
import styles from './StaffLayout.module.css'

const menuItems = [
  { path: '/dashboard', label: 'Dashboard', icon: '📊', roles: ['ADMIN','DOCTOR'] },
  { path: '/patients', label: 'Patients', icon: '👤', roles: ['ADMIN','DOCTOR'] },
  { path: '/appointments', label: 'Appointments', icon: '📅', roles: ['ADMIN','DOCTOR'] },
  { path: '/prescriptions', label: 'Prescriptions', icon: '💊', roles: ['ADMIN','DOCTOR'] },
  { path: '/billing', label: 'Billing', icon: '💰', roles: ['ADMIN','DOCTOR'] },
  { path: '/referrals', label: 'Referrals', icon: '🏥', roles: ['ADMIN','DOCTOR'] },
  { path: '/lab', label: 'Lab Results', icon: '🧪', roles: ['ADMIN','DOCTOR'] },
  { path: '/loinc', label: 'LOINC Catalog', icon: '📋', roles: ['ADMIN','DOCTOR'] },
  { path: '/chat', label: 'Messages', icon: '💬', roles: ['ADMIN','DOCTOR'] },
  { type: 'divider' } as any,
  { path: '/emergency', label: 'Emergency Access', icon: '🚨', roles: ['ADMIN'] },
  { path: '/system/users', label: 'Users', icon: '👥', roles: ['ADMIN'] },
  { path: '/system/roles', label: 'Roles', icon: '🔑', roles: ['ADMIN'] },
  { path: '/system/menus', label: 'Menus', icon: '📋', roles: ['ADMIN'] },
  { path: '/system/quality', label: 'eCQM Quality', icon: '📈', roles: ['ADMIN'] },
  { path: '/system/keys', label: 'Key Management', icon: '🔐', roles: ['ADMIN'] },
  { path: '/audit-logs', label: 'Audit Logs', icon: '📋', roles: ['ADMIN'] },
]

export default function StaffLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [roles, setRoles] = useState<string[]>([])

  useEffect(() => { setRoles(getUserRoles()) }, [])

  const visibleItems = menuItems.filter(item =>
    item.type === 'divider' || hasAnyRole(item.roles))

  const handleLogout = async () => {
    try { await logout() } catch {} // best-effort: trigger audit trail
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    navigate('/login')
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside className={styles.sidebar}>
        <div className={styles.logo}>Medical System</div>
        <div style={{ padding: '0 16px 8px', fontSize: 12, color: '#9ca3af' }}>
          {localStorage.getItem('username') || 'Staff'}
        </div>
        <nav>
          {visibleItems.map((item, i) => {
            if (item.type === 'divider') return <hr key={i} className={styles.divider} />
            return (
              <div key={item.path}
                className={`${styles.menuItem} ${location.pathname.startsWith(item.path) ? styles.active : ''}`}
                onClick={() => navigate(item.path)}>
                <span>{item.icon}</span> {item.label}
              </div>
            )
          })}
        </nav>
        <div style={{ marginTop: 'auto', padding: '16px' }}>
          <div style={{ marginBottom: 8 }}>
            <div style={{ fontSize: 11, color: '#6b7280', marginBottom: 4, paddingLeft: 12 }}>Export CSV</div>
            <div className={styles.menuItem} style={{ fontSize: 13 }} onClick={() => { downloadPatientsCsv().catch(() => alert('Export failed')) }}>
              <span>📥</span> Patients
            </div>
            <div className={styles.menuItem} style={{ fontSize: 13 }} onClick={() => { downloadBillsCsv().catch(() => alert('Export failed')) }}>
              <span>📥</span> Bills
            </div>
          </div>
          <div className={styles.menuItem} onClick={() => navigate('/profile')}>
            <span>🔧</span> Profile
          </div>
          <button onClick={handleLogout} className={styles.logoutBtn}>Logout</button>
        </div>
      </aside>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}
