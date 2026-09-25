/**
 * "Explore" — a small shell hosting two related tools that share the same data
 * shape: Compare (2-4 foods side by side) and Advanced search (rule-based
 * filters). Port of FoodExplorerView.java.
 */

import { useState } from 'react';

import { Tabs } from '../components/ui';
import { AdvancedSearch } from './explore/AdvancedSearch';
import { Compare } from './explore/Compare';

const TABS = [
  { id: 'compare' as const, label: 'Compare' },
  { id: 'advanced' as const, label: 'Advanced search' },
];

type TabId = (typeof TABS)[number]['id'];

export function ExploreView() {
  const [tab, setTab] = useState<TabId>('compare');
  return (
    <>
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      <div className="view-scroll">{tab === 'compare' ? <Compare /> : <AdvancedSearch />}</div>
    </>
  );
}
