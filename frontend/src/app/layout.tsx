import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: {
    default: 'FamilyAgent | 家庭记忆与协作空间',
    template: '%s | FamilyAgent',
  },
  description: '围绕家庭记忆、共享协作与长期陪伴构建的 FamilyAgent 前端工作台。',
  applicationName: 'FamilyAgent',
  keywords: ['FamilyAgent', '家庭记忆', '家庭传承', '家庭协作', '陪伴式 AI', '成长支持'],
  icons: {
    icon: '/icon.svg',
    shortcut: '/icon.svg',
    apple: '/icon.svg',
  },
};

export const viewport: Viewport = {
  themeColor: '#1f6b57',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body className="font-sans">{children}</body>
    </html>
  );
}
