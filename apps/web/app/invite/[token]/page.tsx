"use client";

import { useEffect } from "react";
import { useParams } from "next/navigation";

export default function LegacyInvitationPage(){
  const params=useParams<{token:string}>();
  useEffect(()=>{const token=params?.token;if(token)window.location.replace(`/?invite=${encodeURIComponent(token)}`)},[params]);
  return <main style={{minHeight:"100vh",display:"grid",placeItems:"center",fontFamily:"sans-serif"}}>초대 링크를 확인하는 중…</main>;
}
