import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: '家族教育Agent',
  description: 'AI驱动的家族教育与管理平台',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
