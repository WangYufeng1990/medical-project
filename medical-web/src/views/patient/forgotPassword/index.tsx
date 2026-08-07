import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import axios, { AxiosError } from 'axios'
import styles from '../../login/style.module.css'

export default function PatientForgotPassword() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [username, setUsername] = useState('')
  const [token, setToken] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  const handleRequest = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setInfo('')
    try {
      await axios.post('/api/v1/patient/forgot-password', { username })
      // Same response whether or not the account exists (no enumeration).
      setInfo('If that username exists, a reset token has been generated. Dev mode: check the server console.')
      setStep(2)
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>
      setError(axiosErr?.response?.data?.message || (err instanceof Error ? err.message : '') || 'Request failed')
    }
  }

  const handleReset = async (e: FormEvent) => {
    e.preventDefault(); setError('')
    if (newPassword !== confirm) { setError('Passwords do not match'); return }
    try {
      await axios.post('/api/v1/patient/reset-password', { token, newPassword })
      navigate('/patient/login')
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>
      setError(axiosErr?.response?.data?.message || (err instanceof Error ? err.message : '') || 'Reset failed')
    }
  }

  return (
    <div className={styles.container}>
      {step === 1 ? (
        <form className={styles.card} onSubmit={handleRequest}>
          <h2>Forgot Password</h2>
          {info && <div style={{ color: '#67C23A', fontSize: 13, marginBottom: 8, lineHeight: 1.5 }}>{info}</div>}
          {error && <div className={styles.error}>{error}</div>}
          <input className={styles.input} placeholder="Username" value={username} onChange={e => setUsername(e.target.value)} autoFocus />
          <button className={styles.btn} type="submit" disabled={!username.trim()}>Send Reset Token</button>
        </form>
      ) : (
        <form className={styles.card} onSubmit={handleReset}>
          <h2>Reset Password</h2>
          {info && <div style={{ color: '#67C23A', fontSize: 13, marginBottom: 8, lineHeight: 1.5 }}>{info}</div>}
          {error && <div className={styles.error}>{error}</div>}
          <input className={styles.input} placeholder="Reset token" value={token} onChange={e => setToken(e.target.value)} autoFocus />
          <input className={styles.input} type="password" placeholder="New password" value={newPassword} onChange={e => setNewPassword(e.target.value)} />
          <input className={styles.input} type="password" placeholder="Confirm new password" value={confirm} onChange={e => setConfirm(e.target.value)} />
          <button className={styles.btn} type="submit" disabled={!token.trim() || !newPassword || !confirm}>Reset Password</button>
          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <a href="#" style={{ fontSize: 13, color: '#409EFF', textDecoration: 'none' }} onClick={e => { e.preventDefault(); setStep(1); setInfo(''); setError('') }}>← Back</a>
          </div>
        </form>
      )}
    </div>
  )
}
