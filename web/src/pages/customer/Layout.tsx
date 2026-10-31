import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { Icon } from '../../components/Icon'
import { useI18n } from '../../i18n'

const items = [
  { to: '/', icon: 'home', k: 'nav.home' }, { to: '/explore', icon: 'search', k: 'nav.explore' }, { to: '/bookings', icon: 'calendar', k: 'nav.bookings' },
  { to: '/favorites', icon: 'heart', k: 'nav.favorites' }, { to: '/profile', icon: 'user', k: 'nav.profile' },
] as const

export function CustomerLayout() {
  const { t } = useI18n()
  const { pathname } = useLocation()
  const hideNav = ['/onboarding', '/login', '/register', '/reset'].includes(pathname) || pathname.startsWith('/book/')
  return (
    <div className="shell">
      <div className="app">
        <Outlet />
        {!hideNav && (
          <nav className="bottomnav" aria-label="main">
            {items.map((i) => (
              <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
                <Icon name={i.icon} size={24} /><span>{t(i.k)}</span>
              </NavLink>
            ))}
          </nav>
        )}
      </div>
    </div>
  )
}
