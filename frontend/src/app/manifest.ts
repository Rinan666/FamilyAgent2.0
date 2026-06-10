import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'FamilyAgent | Family Memory and Legacy',
    short_name: 'FamilyAgent',
    description: 'An AI system for family memory, shared wisdom, mirror companionship, and growth support.',
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
