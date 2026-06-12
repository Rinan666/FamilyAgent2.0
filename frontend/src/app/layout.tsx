import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: {
    default: 'FamilyAgent | 家庭记忆与传承',
    template: '%s | FamilyAgent',
  },
  description: '一个围绕家庭记忆、共享智慧、镜像陪伴与成长支持构建的 AI 系统。',
  applicationName: 'FamilyAgent',
  keywords: ['FamilyAgent', '家庭记忆', '家庭传承', '家庭陪伴 AI', '成长支持', '镜像陪伴'],
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
