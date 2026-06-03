import { useState, useEffect, FormEvent } from 'react'
import { getRolePage, createRole, updateRole, deleteRole } from '../../../api/role'
import styles from '../../shared.module.css'
const ef: any = { roleName: '', roleCode: '', description: '', status: 1 }

export default function Roles() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...ef })

  useEffect(() => { getRolePage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) }) }, [page])

  const openForm = (row?: any) => { if (row) { setEditId(row.id); setForm(row) } else { setEditId(null); setForm({ ...ef }) }; setShowForm(true) }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    editId ? await updateRole(editId, form) : await createRole(form)
    setShowForm(false); getRolePage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) })
  }

  return (<div>
    <h2>Roles</h2>
    <button className={styles.btnPrimary} onClick={() => openForm()}>+ Add</button>
    <table className={styles.table}><thead><tr><th>ID</th><th>Name</th><th>Code</th><th></th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id}><td>{r.id}</td><td>{r.roleName}</td><td>{r.roleCode}</td>
        <td><button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>
          <button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deleteRole(r.id); setData(d => d.filter(x => x.id !== r.id)) } }}>Del</button></td></tr>))}</tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*10>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? 'Edit' : 'Add'} Role</h3>
      <form onSubmit={handleSubmit}>
        {['roleName','roleCode','description'].map(f => (<div key={f} className={styles.formGroup} style={{marginBottom:8}}><label>{f}</label><input value={form[f]||''} onChange={e=>setForm({...form,[f]:e.target.value})} /></div>))}
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={()=>setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div></form></div></div>}
  </div>)
}
