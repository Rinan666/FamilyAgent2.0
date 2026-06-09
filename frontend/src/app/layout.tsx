import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: {
    default: 'FamilyAgent | 家族记忆与传承',
    template: '%s | FamilyAgent',
  },
  description: '面向有传承意识家庭的家族记忆、家风经验、镜像陪伴与成长守护 AI 系统。',
  applicationName: 'FamilyAgent',
  keywords: ['FamilyAgent', '家族记忆', '家风传承', '家庭陪伴 AI', '成长守护', '镜像 Agent'],
  icons: {
    icon: '/icon.svg',
    shortcut: '/icon.svg',
    apple: '/icon.svg',
  },
};

export const viewport: Viewport = {
  themeColor: '#8B5E3C',
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
