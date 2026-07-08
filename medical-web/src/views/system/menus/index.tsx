import { useState, useEffect, FormEvent } from 'react'
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '../../../api/menu'
import styles from '../../shared.module.css'
const ef: any = { parentId: 0, menuName: '', path: '', component: '', icon: '', type: 'MENU', permission: '', sort: 0, status: 1 }

export default function Menus() {
  const [data, setData] = useState<any[]>([])
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...ef })

  useEffect(() => { getMenuTree().then(setData) }, [])

  const openForm = (row?: any) => { if (row) { setEditId(row.id); setForm(row) } else { setEditId(null); setForm({ ...ef }) }; setShowForm(true) }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); editId ? await updateMenu(editId, form) : await createMenu(form)
    setShowForm(false); getMenuTree().then(setData)
  }

  return (<div>
    <h2>Menus</h2><button className={styles.btnPrimary} onClick={() => openForm()}>+ Add</button>
    <table className={styles.table} style={{marginTop:16}}><thead><tr><th>ID</th><th>Name</th><th>Path</th><th>Permission</th><th></th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id}><td>{r.id}</td><td>{r.menuName}</td><td>{r.path}</td><td>{r.permission}</td>
        <td><button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>
          <button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deleteMenu(r.id); getMenuTree().then(setData) } }}>Del</button></td></tr>))}</tbody></table>
    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? 'Edit' : 'Add'} Menu</h3>
      <form onSubmit={handleSubmit}>
        {['menuName','path','component','icon','permission'].map(f => (<div key={f} className={styles.formGroup} style={{marginBottom:8}}><label>{f}</label><input value={form[f] ?? ''} onChange={e=>setForm({...form,[f]:e.target.value})} /></div>))}
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={()=>setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div></form></div></div>}
  </div>)
}
