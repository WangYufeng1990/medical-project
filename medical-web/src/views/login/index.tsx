import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { login } from '../../api/auth'
import styles from './style.module.css'

export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setError('')
    try {
      const data = await login({ username, password })
      localStorage.setItem('token', data.token)
      localStorage.setItem('refreshToken', data.refreshToken || '')
      localStorage.setItem('username', data.username)
      navigate('/dashboard')
    } catch (err: any) {
      setError(err?.message || 'Login failed')
    }
  }

  return (
    <div className={styles.container}>
      <form className={styles.card} onSubmit={handleSubmit}>
        <h2>Staff Login</h2>
        {error && <div className={styles.error}>{error}</div>}
        <input className={styles.input} placeholder="Username" value={username} onChange={e => setUsername(e.target.value)} autoFocus />
        <input className={styles.input} type="password" placeholder="Password" value={password} onChange={e => setPassword(e.target.value)} />
        <button className={styles.btn} type="submit">Login</button>
      </form>
    </div>
  )
}
