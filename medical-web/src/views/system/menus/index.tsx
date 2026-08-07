import { useQuery } from '@tanstack/react-query'
import { getMenuTree } from '../../../api/menu'
import { MenuVO } from '../../../types/entities'
import styles from '../../shared.module.css'

type FlatMenu = MenuVO & { _indent: number }

export default function Menus() {
  const { data: tree } = useQuery({
    queryKey: ['menus', 'tree'],
    queryFn: () => getMenuTree(),
    staleTime: 5 * 60_000,
  })
  const nodes = tree ?? []

  const flatten = (nodes: MenuVO[], level: number = 0): FlatMenu[] => {
    const result: FlatMenu[] = []
    nodes.forEach(n => {
      result.push({ ...n, _indent: level })
      if (n.children && n.children.length > 0) {
        result.push(...flatten(n.children, level + 1))
      }
    })
    return result
  }

  const flat = flatten(nodes)

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Menus (Read-only)</h2>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Name</th><th>Path</th><th>Type</th><th>Permission</th><th>Sort</th></tr></thead>
        <tbody>
          {flat.map(r => (
            <tr key={r.id}>
              <td>{r.id}</td>
              <td style={{ paddingLeft: 14 + r._indent * 24 }}>{r._indent > 0 ? '└ ' : ''}{r.menuName}</td>
              <td>{r.path || '-'}</td>
              <td>{r.type}</td>
              <td>{r.permission || '-'}</td>
              <td>{r.sort}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
