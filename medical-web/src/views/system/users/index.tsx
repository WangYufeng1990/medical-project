import { useState, useEffect, FormEvent } from 'react'
import { getUserPage, getUserById, createUser, updateUser, deleteUser } from '../../../api/user'
import styles from '../../shared.module.css'
const emptyForm: any = { username: '', password: '', realName: '', phone: '', email: '', gender: 1, status: 1, npi: '', stateLicenseNumber: '', licenseState: '', deaNumber: '', taxonomyCode: '', credentials: '', specialty: '' }

export default function Users() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...emptyForm })

  useEffect(() => { getUserPage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) }) }, [page])

  const openForm = async (row?: any) => {
    if (row) { setEditId(row.id); const d = await getUserById(row.id); setForm({ ...form, ...d, password: '' }) }
    else { setEditId(null); setForm({ ...emptyForm }) }
    setShowForm(true)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    editId ? await updateUser(editId, form) : await createUser(form)
    setShowForm(false); getUserPage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) })
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>Users</h2>
    <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add User</button>
    <table className={styles.table}><thead><tr><th>ID</th><th>Username</th><th>Name</th><th>NPI</th><th>Specialty</th><th></th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id}><td>{r.id}</td><td>{r.username}</td><td>{r.realName}</td><td>{r.npi}</td><td>{r.specialty}</td>
        <td><button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>
          <button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deleteUser(r.id); setData(d => d.filter(x => x.id !== r.id)) } }}>Del</button></td></tr>))}</tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*10>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? 'Edit' : 'Add'} User</h3>
      <form onSubmit={handleSubmit} className={styles.formGrid}>
        {['username','password','realName','phone','email','npi','stateLicenseNumber','licenseState','deaNumber','taxonomyCode','credentials','specialty'].map(f => (
          <div key={f} className={styles.formGroup}><label>{f}</label><input type={f==='password'?'password':'text'} value={form[f]||''} onChange={e => setForm({...form,[f]:e.target.value})} /></div>))}
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div></form></div></div>}
  </div>)
}
