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

// const customTheme = createTheme({
//   palette: {
//     background: {
//       default: '#f2cd00',
//     },
//   },
// });


const drawerWidth = 240;

export default function DashboardLayout() {

  const { studentUploadStatus, 
          teacherUploadStatus, 
          studentValStatus, 
          setStudentValStatus, 
          setTeacherValStatus, 
          teacherValStatus,
          isCheckedStudentBox,
          setIsCheckedStudentBox,
          isCheckedTeacherBox,
          setIsCheckedTeacherBox
        } = useUpload(); 
  const [showStudentValProgressBar, setShowStudentValProgressBar] = useState(false); 
  const [showTeacherValProgressBar, setShowTeacherValProgressBar] = useState(false);
  const [showRunSchedulerButton, setShowRunSchedulerButton] = useState(false);
  
  console.log(studentUploadStatus, teacherUploadStatus); 
  useEffect(() => { 
    if (studentUploadStatus == 'complete' && teacherUploadStatus == 'complete'){
      setShowStudentValProgressBar(true); 
      setStudentValStatus('checking'); 
    }
    if (teacherUploadStatus == 'complete' && studentValStatus == 'complete'){
      setShowTeacherValProgressBar(true);
      setStudentValStatus('complete'); 
      setIsCheckedStudentBox(true);
      setTeacherValStatus('checking'); 
    }
    if (teacherValStatus == 'complete'){
      setTeacherValStatus('complete'); 
      setIsCheckedTeacherBox(true);
      setShowRunSchedulerButton(true);
    }
  }, [studentUploadStatus, studentValStatus, teacherUploadStatus, teacherValStatus]);
  
  return (
  //<ThemeProvider theme={customTheme}>
   //<CssBaseline/>
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: 1300 }}>
        <Toolbar style={{backgroundColor: "#007c41"}}>
          <Typography variant="h6" color={'#f2cd00'} noWrap>MediScheduler</Typography>
          <Typography variant="b2" color={'#f2cd00'} sx={{paddingLeft:'60em;'}} noWrap>Automated scheduling for medical student clerkship rotations.</Typography>
        </Toolbar>
        
      </AppBar>
      <Drawer variant="permanent" 
              sx={{ 
                width: drawerWidth,
                backgroundColor: "#FFFFFF",
                display: {
                  xs: 'none', 
                  sm: 'none',
                  md: 'block'
                },
                [`& .MuiDrawer-paper`]: 
                { width: drawerWidth } 
                }}>
        <Toolbar/>
        <br/>
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
      <Box component="main" sx={{ flexGrow: 1, p: 3, ml: `${drawerWidth}px`}}>
        <Toolbar/> 
       <Box sx={{
            position: 'fixed', 
            bottom:20,
            top:100,
            left:300,
            right:150,
            display: {
              sm: 'none', 
              md: 'block'
            }, 
            backgroundColor:"#FFFFFF"
            }}>
              <Stack spacing={2} sx={{ flexGrow: 1 }}>
                <br />
                {showStudentValProgressBar && <StudentValProgressBar/>}
                <br/> 
                {showTeacherValProgressBar && <TeacherValProgressBar/>}
                <br/>
                {showRunSchedulerButton && <RunScheduler/>}
              </Stack>
        </Box>
        <Box sx={{
            position: 'fixed', 
            bottom:50,
            top:400,
            right:-15
            }}>
              <img src={homepage2} style={{width:"100%", height:"100%"}}></img>
        </Box>

        <Box sx={{ 
            position: 'fixed', 
            bottom:700,
            top: 875,
            right:45,
            left:290,
            center: 32, 
            gap: 0, 
            display:'flex'
            }}> 
            <Typography 
              variant="b3" 
              opacity='0.5' 
              color='#007c41'
              display='flex'
              sx={{display:{
              xs:'none',
              sm:'none',
              md:'none',
              lg:'flex'      
              }}}>Completely free and open-source. Developed at the University of Alberta.</Typography>        

        </Box>
        
      </Box> 
      <Box component="main" sx={{ flexGrow: 1, p: 3, ml: `${drawerWidth}px` }}>
        <Toolbar />
        <Outlet/> 
        <Box sx={{ 
            position: 'fixed', 
            bottom: 16, 
            right: 16, 
            display: 'flex', 
            gap: 2, 
            }}>
       
              <StudentFileUploadBtn/>
              <TeacherFileUploadBtn/> 
        </Box>
      </Box> 
    </Box> 
  //</ThemeProvider>
  );
}

