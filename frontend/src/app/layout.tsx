import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: {
    default: 'FamilyAgent | Family Memory and Legacy',
    template: '%s | FamilyAgent',
  },
  description: 'An AI system for family memory, shared wisdom, mirror companionship, and growth support.',
  applicationName: 'FamilyAgent',
  keywords: ['FamilyAgent', 'family memory', 'family legacy', 'family companion AI', 'growth support', 'mirror agent'],
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
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
