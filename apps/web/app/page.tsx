import type { Metadata } from "next";
import { MoaDayApp } from "./components/MoaDayApp";

export const metadata: Metadata = {
  title: "MoaDay — 가족과 친구의 일정·자료·쿠폰 공유",
  description: "가족과 친구의 일정, 글과 파일, 모바일 쿠폰을 한 공간에서 함께 관리하세요.",
};

export default function Home() {
  return <MoaDayApp />;
}
