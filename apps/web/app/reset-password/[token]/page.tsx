"use client";

import { FormEvent, useState } from "react";
import { useParams } from "next/navigation";
import { api } from "../../lib/api";
import styles from "../../components/CouponWithApp.module.css";

export default function PasswordResetPage() {
  const params = useParams<{ token: string }>();
  const [busy, setBusy] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password"));
    const confirmation = String(form.get("confirmation"));
    if (password !== confirmation) {
      setError("새 비밀번호가 서로 일치하지 않습니다.");
      return;
    }
    setBusy(true);
    try {
      await api.resetPassword(params.token, password);
      setCompleted(true);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "비밀번호를 재설정하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return <main className={styles.authPage}>
    <section className={styles.authIntro}>
      <div className={styles.brand}><span className={styles.brandMark}>M</span><span>MoaDay</span></div>
      <p className={styles.kicker}>안전한 계정 복구</p>
      <h1>새 비밀번호로<br />다시 시작하세요.</h1>
      <p>복구 링크는 30분 동안 한 번만 사용할 수 있으며, 변경하면 기존 로그인은 모두 만료됩니다.</p>
    </section>
    <section className={styles.authPanel}>
      <p className={styles.eyebrow}>MoaDay 계정 보안</p>
      <h2>{completed ? "변경 완료" : "새 비밀번호 설정"}</h2>
      {completed ? <div className={styles.form}>
        <p className={styles.success} role="status">비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.</p>
        <button type="button" className={styles.primaryButtonLarge} onClick={() => window.location.replace("/")}>로그인 화면으로 이동</button>
      </div> : <form className={styles.form} onSubmit={submit}>
        <label>새 비밀번호<input required minLength={8} maxLength={72} type="password" name="password" autoComplete="new-password" placeholder="8자 이상" /></label>
        <label>새 비밀번호 확인<input required minLength={8} maxLength={72} type="password" name="confirmation" autoComplete="new-password" placeholder="한 번 더 입력" /></label>
        {error && <p className={styles.error} role="alert">{error}</p>}
        <button className={styles.primaryButtonLarge} disabled={busy}>{busy ? "변경 중…" : "비밀번호 변경"}</button>
        <button type="button" className={styles.textButton} onClick={() => window.location.replace("/")}>로그인 화면으로 돌아가기</button>
      </form>}
    </section>
  </main>;
}
