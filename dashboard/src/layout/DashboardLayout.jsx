import React, { useState, useEffect } from 'react';
import { AppBar, Toolbar, Typography, Drawer, List, ListItem, ListItemText, Box, Stack, Avatar, IconButton, Tooltip } from '@mui/material';
import { Logout as LogoutIcon } from '@mui/icons-material';
import { Link, Outlet } from 'react-router-dom';
import { useAuth } from '../components/AuthContext';
import TeacherFileUploadBtn from '../components/TeacherFileUploadBtn';
import StudentFileUploadBtn from '../components/StudentFileUploadBtn';
import ValidationModal from '../components/ValidationModal';
import { useUpload } from '../components/UploadContext';

const drawerWidth = 240;

export default function DashboardLayout() {
  const { user, logout } = useAuth();

  const {
    studentUploadStatus,
    teacherUploadStatus,
    studentValStatus,
    setStudentValStatus,
    setTeacherValStatus,
    teacherValStatus,
    setIsCheckedStudentBox,
    setIsCheckedTeacherBox,
  } = useUpload();

  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    // Open modal when both uploads are complete
    if (studentUploadStatus === 'complete' && teacherUploadStatus === 'complete') {
      setModalOpen(true);
      if (studentValStatus === 'unknown') {
        setStudentValStatus('checking');
      }
    }
    if (studentValStatus === 'complete' && teacherUploadStatus === 'complete') {
      setIsCheckedStudentBox(true);
      if (teacherValStatus === 'unknown') {
        setTeacherValStatus('checking');
      }
    }
    if (teacherValStatus === 'complete') {
      setIsCheckedTeacherBox(true);
    }
  }, [studentUploadStatus, studentValStatus, teacherUploadStatus, teacherValStatus]);

  return (
    <Box sx={{ display: 'flex' }}>
      {/* Top AppBar */}
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

      {/* Side Drawer */}
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

      {/* Main Content */}
      <Box component="main" sx={{ flexGrow: 1, p: 3, ml: { md: `${drawerWidth}px` } }}>
        <Toolbar />
        <Outlet />

        {/* Validation modal */}
        <ValidationModal open={modalOpen} onClose={() => setModalOpen(false)} />

        {/* Upload buttons fixed to bottom-right */}
        <Box sx={{ position: 'fixed', bottom: 24, right: 24, display: 'flex', gap: 2 }}>
          <StudentFileUploadBtn />
          <TeacherFileUploadBtn />
        </Box>

        {/* Footer */}
        <Box sx={{ position: 'fixed', bottom: 8, left: drawerWidth + 24, display: { xs: 'none', lg: 'block' } }}>
          <Typography variant="caption" color="#007c41" sx={{ opacity: 0.7 }}>
            Completely free and open-source. Developed at the University of Alberta.
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
