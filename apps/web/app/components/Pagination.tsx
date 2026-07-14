"use client";

import styles from "./CouponWithApp.module.css";

export function Pagination({ page, totalItems, pageSize, onChange }: { page: number; totalItems: number; pageSize: number; onChange: (page: number) => void }) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  if (totalPages <= 1) return null;
  return <nav className={styles.pagination} aria-label="목록 페이지">
    <button type="button" disabled={page <= 1} onClick={() => onChange(page - 1)}>이전</button>
    <span><strong>{page}</strong> / {totalPages}</span>
    <button type="button" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>다음</button>
  </nav>;
}
