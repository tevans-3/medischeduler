import React from 'react';
import { useState, useEffect } from 'react';
import { AppBar, Toolbar, Typography, Drawer, List, ListItem, ListItemText, Box, Stack } from '@mui/material';
import { Link, Outlet } from 'react-router-dom';
import TeacherFileUploadBtn from '../components/TeacherFileUploadBtn';
import StudentFileUploadBtn from '../components/StudentFileUploadBtn';
import StudentValProgressBar from '../components/StudentValProgressBar';
import TeacherValProgressBar from '../components/TeacherValProgressBar';  
import RunScheduler from '../components/RunButton'; 
import { useUpload } from '../components/UploadContext';
import homepage2 from './homepage2.png';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Checkbox from '@mui/material/Checkbox'; 


const drawerWidth = 240;

export default function DashboardLayout() {

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: 1300 }}>
        <Toolbar style={{backgroundColor: "#007c41"}}>
          <Typography variant="h6" color={'#f2cd00'} noWrap>MediScheduler</Typography>
          <Typography variant="b2" color={'#f2cd00'} sx={{paddingLeft:'40em;'}} noWrap>Automated scheduling for medical student clerkship rotations.</Typography>
        </Toolbar>
        
      </AppBar>
      <Drawer variant="permanent" sx={{ width: drawerWidth, [`& .MuiDrawer-paper`]: { width: drawerWidth } }}>
        <Toolbar />
        <List>
          <ListItem button component={Link} to="/about">
            <ListItemText primary="User Documentation" />
          </ListItem>
          <ListItem button component={Link} to="/privacy"> 
            <ListItemText primary="Privacy Statement"/>
          </ListItem>
          <ListItem button component={Link} to="https://github.com/tevans-3/MediScheduler">
            <ListItemText primary="Source Code"></ListItemText>
          </ListItem>
        </List>
      </Drawer>
      <Outlet/>
            <Box component="main" sx={{ flexGrow: 1, p: 3, ml: `${drawerWidth}px` }}>
            </Box>
        <Toolbar />
    
   </Box>
  //</ThemeProvider>
  );
}

