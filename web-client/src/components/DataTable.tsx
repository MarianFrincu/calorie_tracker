/**
 * Selectable table + "Load more" bar — the web twin of the JavaFX TableView
 * plus Pager.loadMoreBar() pairing used all over the desktop client.
 */

import type { ReactNode } from 'react';
import type { Pager } from './usePager';

export interface Column<T> {
  key: string;
  header: string;
  /** Right-aligns and uses tabular figures. */
  numeric?: boolean;
  width?: number;
  render: (row: T) => ReactNode;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  /** Placeholder shown when `rows` is empty. */
  placeholder: ReactNode;
  selectedKey?: string | number | null;
  onSelect?: (row: T) => void;
  /** Fired on double click / Enter — used by pickers to mean "choose this". */
  onActivate?: (row: T) => void;
  maxHeight?: number;
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  placeholder,
  selectedKey,
  onSelect,
  onActivate,
  maxHeight,
}: DataTableProps<T>) {
  return (
    <div className="table-wrap" style={maxHeight ? { maxHeight } : undefined}>
      <table className="data">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={c.numeric ? 'num' : undefined} style={{ width: c.width }}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>
                <div className="table-placeholder">{placeholder}</div>
              </td>
            </tr>
          ) : (
            rows.map((row) => {
              const key = rowKey(row);
              return (
                <tr
                  key={key}
                  className={[
                    onSelect ? 'selectable' : '',
                    selectedKey != null && selectedKey === key ? 'selected' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  tabIndex={onSelect ? 0 : undefined}
                  onClick={onSelect ? () => onSelect(row) : undefined}
                  onDoubleClick={onActivate ? () => onActivate(row) : undefined}
                  onKeyDown={
                    onSelect
                      ? (e) => {
                          if (e.key === 'Enter') {
                            onSelect(row);
                            onActivate?.(row);
                          }
                        }
                      : undefined
                  }
                >
                  {columns.map((c) => (
                    <td key={c.key} className={c.numeric ? 'num' : undefined}>
                      {c.render(row)}
                    </td>
                  ))}
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}

export function LoadMoreBar<T>({ pager }: { pager: Pager<T> }) {
  if (pager.error) {
    return (
      <div className="row">
        <span className="muted" style={{ color: 'var(--danger-strong)' }}>
          {pager.error}
        </span>
        <button type="button" className="btn" onClick={pager.refresh}>
          Retry
        </button>
      </div>
    );
  }
  if (!pager.hasMore) {
    return pager.loading ? <span className="muted">Loading…</span> : null;
  }
  return (
    <div className="row">
      <button type="button" className="btn" onClick={pager.loadMore} disabled={pager.loading}>
        {pager.loading ? 'Loading…' : 'Load more'}
      </button>
      <span className="muted">{pager.items.length} shown</span>
    </div>
  );
}

/** Search box wired to a pager — Enter or the button runs the search. */
export function SearchBar<T>({
  pager,
  placeholder,
  children,
}: {
  pager: Pager<T>;
  placeholder: string;
  /** Extra toolbar controls rendered after the search button. */
  children?: ReactNode;
}) {
  return (
    <form
      className="row grow"
      onSubmit={(e) => {
        e.preventDefault();
        const input = e.currentTarget.elements.namedItem('q') as HTMLInputElement | null;
        pager.search(input?.value ?? '');
      }}
    >
      <input
        name="q"
        type="text"
        className="grow"
        placeholder={placeholder}
        defaultValue={pager.query}
        aria-label={placeholder}
      />
      <button type="submit" className="btn">
        Search
      </button>
      {children}
    </form>
  );
}
