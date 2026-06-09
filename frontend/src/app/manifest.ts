import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'FamilyAgent | 家族记忆与传承',
    short_name: 'FamilyAgent',
    description: '面向有传承意识家庭的家族记忆、家风经验、镜像陪伴与成长守护 AI 系统。',
    theme_color: '#8B5E3C',
    background_color: '#F5E8D8',
    display: 'standalone',
    start_url: '/',
    icons: [
      {
        src: '/icon.svg',
        sizes: 'any',
        type: 'image/svg+xml',
        purpose: 'any',
      },
    ],
  };
}
