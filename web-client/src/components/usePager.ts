/**
 * Port of desktop-client's `Pager`: search + accumulate 20 rows at a time with
 * a "Load more" button.
 *
 * The backend returns a bare `List<T>` with no total count, so "is there
 * another page?" is inferred the same way the desktop client infers it — a
 * short page means we reached the end.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { friendlyMessage } from './Toast';

export const PAGE_SIZE = 20;

type Loader<T> = (query: string, page: number, size: number, signal: AbortSignal) => Promise<T[]>;

export interface Pager<T> {
  items: T[];
  query: string;
  loading: boolean;
  error: string | null;
  hasMore: boolean;
  /** Run a fresh search from page 0. */
  search: (query: string) => void;
  loadMore: () => void;
  /** Re-run the current search (after a create/update/delete). */
  refresh: () => void;
}

export function usePager<T>(loader: Loader<T>, initialQuery = ''): Pager<T> {
  const [items, setItems] = useState<T[]>([]);
  const [query, setQuery] = useState(initialQuery);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);

  // Keep the loader in a ref so callers can pass an inline arrow without
  // retriggering the effect on every render.
  const loaderRef = useRef(loader);
  loaderRef.current = loader;

  // Bumping this re-runs the fetch effect even when query/page are unchanged.
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);

    loaderRef
      .current(query, page, PAGE_SIZE, controller.signal)
      .then((rows) => {
        if (controller.signal.aborted) return;
        // page 0 replaces, later pages append.
        setItems((current) => (page === 0 ? rows : [...current, ...rows]));
        setHasMore(rows.length === PAGE_SIZE);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(friendlyMessage(err));
        setHasMore(false);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [query, page, nonce]);

  const search = useCallback((next: string) => {
    setQuery(next);
    setPage(0);
    setItems([]);
    // A repeated search of the same text still has to re-hit the server.
    setNonce((n) => n + 1);
  }, []);

  const loadMore = useCallback(() => setPage((p) => p + 1), []);

  const refresh = useCallback(() => {
    setPage(0);
    setItems([]);
    setNonce((n) => n + 1);
  }, []);

  return { items, query, loading, error, hasMore, search, loadMore, refresh };
}
