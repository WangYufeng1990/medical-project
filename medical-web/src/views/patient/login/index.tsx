import { useState, FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import axios, { AxiosError } from 'axios'
import { scheduleProactiveRefresh } from '../../../api/patientRequest'
import { Result } from '../../../types/common'
import { PatientLoginResponse } from '../../../types/entities'
import { tokenStore } from '../../../utils/auth'
import styles from '../../login/style.module.css'

export default function PatientLogin() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setError('')
    try {
      const res = await axios.post<Result<PatientLoginResponse>>('/api/v1/patient/login', { username, password })
      const data = res.data.data!
      tokenStore.set('patientToken', data.token)
      if (data.refreshToken) tokenStore.set('patientRefreshToken', data.refreshToken)
      tokenStore.set('patientInfo', JSON.stringify({ patientId: data.patientId, name: data.name, username: data.username }))
      scheduleProactiveRefresh()
      navigate('/patient/dashboard')
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>
      setError(axiosErr?.response?.data?.message || (err instanceof Error ? err.message : '') || 'Login failed')
    }
  }

  return (
    <div className={styles.container}>
      <form className={styles.card} onSubmit={handleSubmit}>
        <h2>Patient Login</h2>
        {error && <div className={styles.error}>{error}</div>}
        <input className={styles.input} placeholder="Username" value={username} onChange={e => setUsername(e.target.value)} autoFocus />
        <input className={styles.input} type="password" placeholder="Password" value={password} onChange={e => setPassword(e.target.value)} />
        <button className={styles.btn} type="submit">Login</button>
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Link to="/patient/forgot-password" style={{ fontSize: 13, color: '#409EFF', textDecoration: 'none' }}>Forgot Password?</Link>
        </div>
      </form>
    </div>
  )
}
