import { Navigate, Route, Routes } from 'react-router-dom';
import { homePathFor, useAuth } from './auth/AuthContext';
import RequireAuth from './auth/RequireAuth';
import AppLayout from './components/AppLayout';
import DashboardPage from './pages/DashboardPage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import PolicySearchPage from './pages/PolicySearchPage';
import PremiumPaymentsPage from './pages/PremiumPaymentsPage';
import RecommendationsPage from './pages/RecommendationsPage';
import RegisterPolicyPage from './pages/RegisterPolicyPage';

const CUSTOMER_ONLY = ['CUSTOMER'];

function HomeRedirect() {
  const { user } = useAuth();
  return <Navigate to={homePathFor(user.role)} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<HomeRedirect />} />
        <Route
          path="dashboard"
          element={
            <RequireAuth roles={CUSTOMER_ONLY}>
              <DashboardPage />
            </RequireAuth>
          }
        />
        <Route
          path="recommendations"
          element={
            <RequireAuth roles={CUSTOMER_ONLY}>
              <RecommendationsPage />
            </RequireAuth>
          }
        />
        <Route
          path="register/:productCode"
          element={
            <RequireAuth roles={CUSTOMER_ONLY}>
              <RegisterPolicyPage />
            </RequireAuth>
          }
        />
        <Route path="payments" element={<PremiumPaymentsPage />} />
        <Route path="search" element={<PolicySearchPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
