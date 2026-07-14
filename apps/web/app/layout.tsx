import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "MoaDay",
    template: "%s | MoaDay",
  },
  description: "가족과 친구의 일정, 자료, 쿠폰을 함께 관리하는 생활 협업 서비스",
  manifest: "/manifest.webmanifest",
  openGraph: {
    title: "MoaDay",
    description: "가족과 친구의 일정·자료·쿠폰을 한곳에",
    type: "website",
    locale: "ko_KR",
    images: [{ url: "/og.png", width: 1731, height: 909, alt: "MoaDay 서비스 소개" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "MoaDay",
    description: "가족과 친구의 일정·자료·쿠폰을 한곳에",
    images: ["/og.png"],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
