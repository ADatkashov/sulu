import { Navigate, Route, Routes } from 'react-router-dom'
import { CustomerLayout } from './pages/customer/Layout'
import { Onboarding } from './pages/customer/Onboarding'
import { Login, Register, Reset } from './pages/customer/AuthPages'
import { Home } from './pages/customer/Home'
import { Explore } from './pages/customer/Explore'
import { BusinessPage, ProPage, ServicePage } from './pages/customer/Details'
import { Book } from './pages/customer/Book'
import { Booked, BookingDetail, Bookings } from './pages/customer/Bookings'
import { Favorites, Notifications, Profile } from './pages/customer/Me'
import { BusinessApp } from './pages/business/BusinessApp'
import { ProApp } from './pages/business/ProApp'
import { AdminApp } from './pages/admin/AdminApp'
import { Empty } from './components/ui'
import { Link } from 'react-router-dom'

export default function App() {
  return (
    <Routes>
      <Route path="/business/*" element={<BusinessApp />} />
      <Route path="/admin/*" element={<AdminApp />} />
      <Route path="/pro" element={<ProApp />} />
      <Route element={<CustomerLayout />}>
        <Route index element={<Home />} />
        <Route path="onboarding" element={<Onboarding />} />
        <Route path="login" element={<Login />} /><Route path="register" element={<Register />} /><Route path="reset" element={<Reset />} />
        <Route path="explore" element={<Explore />} />
        <Route path="salon/:id" element={<BusinessPage />} /><Route path="master/:id" element={<ProPage />} /><Route path="service/:id" element={<ServicePage />} />
        <Route path="book/:serviceId" element={<Book />} /><Route path="booked/:id" element={<Booked />} />
        <Route path="bookings" element={<Bookings />} /><Route path="bookings/:id" element={<BookingDetail />} />
        <Route path="favorites" element={<Favorites />} /><Route path="profile" element={<Profile />} /><Route path="notifications" element={<Notifications />} />
        <Route path="home" element={<Navigate to="/" replace />} />
        <Route path="*" element={<div className="page"><Empty icon="search" title="404"><Link to="/" className="btn btn-primary">SULU</Link></Empty></div>} />
      </Route>
    </Routes>
  )
}
