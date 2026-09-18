import { Link, useLocation } from 'react-router-dom'
import { getUserRoles } from '../utils/auth'

/**
 * Catch-all for unknown paths. Without it React Router renders nothing at all,
 * so a mistyped URL was a blank page with no way back (M8 UI pass).
 */
export default function NotFound() {
  const { pathname } = useLocation()
  const isPatient = pathname.startsWith('/patient')
  const home = isPatient
    ? '/patient/dashboard'
    : getUserRoles().length > 0 ? '/dashboard' : '/login'

  return (
    <div style={{ padding: 48 }}>
      <h2>Page not found</h2>
      <p style={{ color: '#909399', marginTop: 8 }}>
        <code>{pathname}</code> is not a page in this application.
      </p>
      <p style={{ marginTop: 16 }}>
        <Link to={home}>Go back to {isPatient ? 'the patient portal' : 'the dashboard'}</Link>
      </p>
    </div>
  )
}
