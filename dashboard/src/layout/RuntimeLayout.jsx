import React from 'react';
import { AppBar, Toolbar, Typography, Drawer, List, ListItem, ListItemText, Box, Stack, Avatar, IconButton, Tooltip } from '@mui/material';
import { Logout as LogoutIcon } from '@mui/icons-material';
import { Link, Outlet } from 'react-router-dom';
import { useAuth } from '../components/AuthContext';

const drawerWidth = 240;

/**
 * Layout for the "running" and "results" pages.
 *
 * Shares the same AppBar and sidebar navigation as the dashboard but
 * leaves the main content area open for progress indicators or the
 * results map.
 */
export default function RuntimeLayout() {
  const { user, logout } = useAuth();

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ backgroundColor: '#007c41' }}>
          <Typography variant="h6" color="#f2cd00" noWrap>
            MediScheduler
          </Typography>
          <Typography variant="body2" color="#f2cd00" sx={{ ml: 'auto', mr: 2 }} noWrap>
            Automated scheduling for medical student clerkship rotations.
          </Typography>
          {user && (
            <Stack direction="row" alignItems="center" spacing={1}>
              <Tooltip title={user.email || ''}>
                <Avatar
                  src={user.picture || ''}
                  alt={user.name || ''}
                  sx={{ width: 32, height: 32 }}
                />
              </Tooltip>
              <Tooltip title="Sign out">
                <IconButton onClick={logout} size="small" sx={{ color: '#f2cd00' }}>
                  <LogoutIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
          )}
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': { width: drawerWidth, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <List>
          <ListItem button component={Link} to="/">
            <ListItemText primary="Home" />
          </ListItem>
          <ListItem button component={Link} to="/docs">
            <ListItemText primary="User Documentation" />
          </ListItem>
          <ListItem button component={Link} to="/privacy">
            <ListItemText primary="Privacy Statement" />
          </ListItem>
          <ListItem button component="a" href="https://github.com/tevans-3/MediScheduler" target="_blank" rel="noopener">
            <ListItemText primary="Source Code" />
          </ListItem>
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3, ml: { md: `${drawerWidth}px` } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
