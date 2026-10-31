import type { SVGProps } from 'react'

const P: Record<string, string> = {
  home: 'M3 11.5 12 4l9 7.5M5.5 10v9.5h13V10',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm9 16-4-4',
  calendar: 'M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7Zm0 4h16M8 3v4m8-4v4',
  heart: 'M12 20s-7.5-4.6-7.5-10A4.5 4.5 0 0 1 12 7.2 4.5 4.5 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10Z',
  user: 'M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Zm-8 9a8 8 0 0 1 16 0',
  bell: 'M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16Zm4 4a2 2 0 0 0 4 0',
  star: 'm12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3Z',
  scissors: 'M6 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm0 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM8.5 7.5 20 18M8.5 16.5 20 6',
  palette: 'M12 3a9 9 0 1 0 0 18c1.4 0 2-1 1.6-2.2-.5-1.3.2-2.3 1.6-2.3H17a4 4 0 0 0 4-4c0-5-4-9.5-9-9.5ZM7.5 11h.01M10 7.5h.01M14.5 7.5h.01',
  hand: 'M8 13V6a1.5 1.5 0 0 1 3 0v5m0-6.5a1.5 1.5 0 0 1 3 0V11m0-4.5a1.5 1.5 0 0 1 3 0V13m0-3a1.5 1.5 0 0 1 3 0v4a7 7 0 0 1-7 7h-1a7 7 0 0 1-5.5-2.7L3 14.5a1.6 1.6 0 0 1 2.4-2L8 15',
  foot: 'M9 3c2 0 3 1.5 3 4s-1 3-1 6c0 3 2 3 2 6a2 2 0 0 1-4 0c0-3-2-4-2-8 0-3 0-8 2-8Zm8 6h.01M15 12h.01M17 15h.01',
  brush: 'M18 3a2 2 0 0 1 3 3l-8 9-4-4 9-8ZM8 12c-2 0-4 1.5-4 4 0 2-1 3-1 4 2 0 5 0 6.5-1.5 1-1 1.5-2.5.5-4',
  eye: 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  droplet: 'M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11Z',
  flask: 'M9 3h6M10 3v6l-5.5 9.5A1.5 1.5 0 0 0 5.8 21h12.4a1.5 1.5 0 0 0 1.3-2.5L14 9V3M7.5 15h9',
  razor: 'M4 8h16v4H4zM6 12v7m4-7v7m4-7v7m4-7v7M8 4h8',
  leaf: 'M20 4C10 4 4 9 4 15a5 5 0 0 0 5 5c6 0 11-6 11-16ZM4 20c3-5 6-8 11-11',
  lotus: 'M12 20c-4 0-8-3-9-8 3 0 5.5 1 7 3 .5-4 1-7 2-10 1 3 1.5 6 2 10 1.5-2 4-3 7-3-1 5-5 8-9 8Z',
  sparkles: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Zm7 11 .9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9L19 14Z',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-13v5l3 2',
  pin: 'M12 21s-7-6-7-11a7 7 0 0 1 14 0c0 5-7 11-7 11Zm0-8a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  chevron: 'm9 6 6 6-6 6', chevronL: 'm15 6-6 6 6 6', check: 'm5 12.5 4.5 4.5L19 7.5', x: 'M6 6l12 12M18 6 6 18', plus: 'M12 5v14M5 12h14',
  phone: 'M6.5 3h3l1.5 4-2 1.5a11 11 0 0 0 6.5 6.5L17 13l4 1.5v3a2.5 2.5 0 0 1-2.5 2.5A15.5 15.5 0 0 1 4 5.5 2.5 2.5 0 0 1 6.5 3Z',
  filter: 'M4 6h16M7 12h10M10 18h4', logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 8l-4 4 4 4M6 12h10', gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7.4-3a7.4 7.4 0 0 0-.1-1.2l2-1.5-2-3.4-2.3.9a7.6 7.6 0 0 0-2-1.2L14.6 3h-4l-.4 2.6a7.6 7.6 0 0 0-2 1.2l-2.3-.9-2 3.4 2 1.5a7.4 7.4 0 0 0 0 2.4l-2 1.5 2 3.4 2.3-.9a7.6 7.6 0 0 0 2 1.2l.4 2.6h4l.4-2.6a7.6 7.6 0 0 0 2-1.2l2.3.9 2-3.4-2-1.5c.1-.4.1-.8.1-1.2Z',
  chart: 'M4 20V10m6 10V4m6 16v-7m4 7H3', users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 10a7 7 0 0 1 14 0m1-17a4 4 0 0 1 0 7.5M22 21a7 7 0 0 0-4-6.3', shield: 'M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3Z', tag: 'M3 12V4h8l10 10-8 8L3 12Zm5-4h.01', list: 'M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01',
  copy: 'M9 9h10v11H9zM5 15V4h10', download: 'M12 4v11m0 0-4-4m4 4 4-4M5 20h14', trash: 'M4 7h16M9 7V4h6v3m-8 0 1 13h8l1-13', map: 'M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2ZM9 4v14m6-12v14',
}

export function Icon({ name, size = 24, ...rest }: { name: string; size?: number } & SVGProps<SVGSVGElement>) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...rest}>
      <path d={P[name] ?? P.sparkles} />
    </svg>
  )
}

export function Logo({ size = 28, text = true }: { size?: number; text?: boolean }) {
  return (
    <span className="logo" aria-label="SULU">
      <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
        <defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#F06AAE" /><stop offset="1" stopColor="#C7357D" /></linearGradient></defs>
        <rect width="64" height="64" rx="18" fill="url(#lg)" />
        <path d="M41 21.5c-2.2-2.6-5.6-4-9.2-4-5.6 0-9.4 2.9-9.4 7.2 0 4.6 3.9 6 8.9 7.2 4.1 1 6.2 1.8 6.2 3.9 0 2-2.1 3.3-5.1 3.3-3 0-5.5-1.3-7.4-3.6" fill="none" stroke="#fff" strokeWidth="5" strokeLinecap="round" />
        <circle cx="44" cy="17" r="3.2" fill="#FFD9EA" />
      </svg>
      {text && <span>SULU</span>}
    </span>
  )
}
