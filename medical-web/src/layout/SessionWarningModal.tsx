import styles from '../views/shared.module.css'

interface Props {
  visible: boolean
  onContinue: () => void
  onLogout: () => void
}

export default function SessionWarningModal({ visible, onContinue, onLogout }: Props) {
  if (!visible) return null
  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modal} style={{ maxWidth: 420 }}>
        <h3>Session Expiring</h3>
        <p style={{ marginBottom: 16, color: '#4b5563', fontSize: 14 }}>
          Your session will expire in 5 minutes due to inactivity. Any activity or
          &quot;Continue Session&quot; keeps you signed in.
        </p>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button className={styles.btnPrimary} onClick={onContinue}>Continue Session</button>
          <button className={styles.btnSm} onClick={onLogout}>Logout Now</button>
        </div>
      </div>
    </div>
  )
}
