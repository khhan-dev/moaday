"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { api } from "../../lib/api";
import styles from "../../components/CouponWithApp.module.css";

export default function VerifyEmailPage() {
  const params = useParams<{ token: string }>();
  const [state, setState] = useState<"loading" | "complete" | "error">("loading");
  const [error, setError] = useState("");
  useEffect(() => { void api.confirmEmailVerification(params.token).then(() => setState("complete")).catch(reason => { setError(reason instanceof Error ? reason.message : "이메일 인증을 완료하지 못했습니다."); setState("error"); }); }, [params.token]);
  return <main className={styles.authPage}><section className={styles.authIntro}><div className={styles.brand}><span className={styles.brandMark}>M</span><span>MoaDay</span></div><p className={styles.kicker}>계정 이메일 인증</p><h1>안전하게<br />계정을 활성화합니다.</h1></section><section className={styles.authPanel}><p className={styles.eyebrow}>MoaDay 시작하기</p><h2>{state === "complete" ? "인증 완료" : state === "error" ? "인증할 수 없음" : "이메일 인증 중"}</h2>{state === "loading" && <p className={styles.authHelp}>인증 링크를 확인하고 있습니다.</p>}{state === "complete" && <div className={styles.form}><p className={styles.success} role="status">이메일 인증이 완료되었습니다. 이제 로그인해 주세요.</p><button type="button" className={styles.primaryButtonLarge} onClick={() => window.location.replace("/")}>로그인 화면으로 이동</button></div>}{state === "error" && <div className={styles.form}><p className={styles.error} role="alert">{error}</p><p className={styles.authHelp}>링크가 만료되었거나 이미 사용되었습니다. 로그인 화면에서 인증 이메일을 다시 보내세요.</p><button type="button" className={styles.primaryButtonLarge} onClick={() => window.location.replace("/")}>로그인 화면으로 이동</button></div>}</section></main>;
}
