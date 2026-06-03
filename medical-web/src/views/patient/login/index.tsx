import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import styles from '../../login/style.module.css'

export default function PatientLogin() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setError('')
    try {
      const res = await axios.post('/api/v1/patient/login', { username, password })
      const data = res.data.data
      localStorage.setItem('patientToken', data.token)
      localStorage.setItem('patientInfo', JSON.stringify({ patientId: data.patientId, name: data.name, username: data.username }))
      navigate('/patient/dashboard')
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Login failed')
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
      </form>
    </div>
  )
}
