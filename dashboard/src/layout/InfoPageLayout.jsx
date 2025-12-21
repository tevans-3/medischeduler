import React from 'react';
import { useState, useEffect } from 'react';
import { AppBar, Toolbar, Typography, Drawer, List, ListItem, ListItemText, Box, Stack } from '@mui/material';
import { Link, Outlet } from 'react-router-dom'; 
import { useUpload } from '../components/UploadContext';
import homepage2 from './homepage2.png';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Checkbox from '@mui/material/Checkbox'; 

// const customTheme = createTheme({
//   palette: {
//     background: {
//       default: '#f2cd00',
//     },
//   },
// });


const drawerWidth = 240;

export default function DashboardLayout() {

  return (
  //<ThemeProvider theme={customTheme}>
   //<CssBaseline/>
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
        <Toolbar />
        
         <Box sx={{ 
            position: 'fixed', 
            bottom:20,
            top: 550,
            right:45,
            left:290,
            center: 32, 
            gap: 0, 
            }}> 
            
              <Typography variant="b3" opacity='0.5' color='#007c41'>Completely free and open-source. Developed at the University of Alberta.</Typography>        
         </Box>
        </Box> 
    </Box> 
  //</ThemeProvider>
  );
}

