import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import DashboardLayout from './layout/DashboardLayout';
import RuntimeLayout from './layout/RuntimeLayout';
import InfoPageLayout from './layout/InfoPageLayout';
import Home from './pages/Home';
import UserDocs from './pages/UserDocs';
import Privacy from './pages/Privacy';
import Running from './pages/Running';
import Results from './pages/Results';
import ProtectedRoute from './components/ProtectedRoute';

const router = createBrowserRouter([
  {
    element: (
      <ProtectedRoute>
        <DashboardLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: '/', element: <Home /> },
    ],
  },
  {
    element: (
      <ProtectedRoute>
        <RuntimeLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: '/running', element: <Running /> },
      { path: '/results', element: <Results /> },
    ],
  },
  {
    element: (
      <ProtectedRoute>
        <InfoPageLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: '/docs', element: <UserDocs /> },
      { path: '/privacy', element: <Privacy /> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
