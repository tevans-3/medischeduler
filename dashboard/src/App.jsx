import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import DashboardLayout from './layout/DashboardLayout';
import RuntimeLayout from './layout/RuntimeLayout'; 
import InfoPageLayout from './layout/InfoPageLayout';
import Home from './pages/Home';
import UserDocs from './pages/UserDocs';
import Privacy from './pages/Privacy';
import Running from './pages/Running';
import { Info } from '@mui/icons-material';

const router = createBrowserRouter([
  {
    element: <DashboardLayout/>,
    children: [
      { path: '/', element: <Home/>}, 
    ]
  }, 
  { element: <RuntimeLayout/>,
    children: [
      { path: "/running", element:<Running/>}
    ]
  },
  {
    element: <InfoPageLayout/>, 
    children: [
      { path: "/docs", element:<UserDocs/>}, 
      { path: "/privacy", element:<Privacy/>}
    ]
  },
]);
export default function App() {
  return (
      <RouterProvider router={router}/>
  );
}
