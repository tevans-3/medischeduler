import { useState, useEffect } from 'react';
import { styled } from '@mui/material/styles';
import { AppBar, Toolbar, Typography, Drawer, List, ListItem, ListItemText, Box, Stack } from '@mui/material';
import LinearProgress, { linearProgressClasses } from '@mui/material/LinearProgress';
import Checkbox from '@mui/material/Checkbox';
import { useUpload } from './UploadContext'; 

const label = { inputProps: { 'aria-label': 'Checkbox demo' } };

const BorderLinearProgress = styled(LinearProgress)(({ theme }) => ({
  height: 75,
  width: '50%',
  borderRadius: 5,
  [`&.${linearProgressClasses.colorPrimary}`]: {
    backgroundColor: theme.palette.grey[200],
    ...theme.applyStyles('dark', {
      backgroundColor: theme.palette.grey[800],
    })
  },
  [`& .${linearProgressClasses.bar}`]: {
    borderRadius: 5,
    backgroundColor: '#007c41',
    ...theme.applyStyles('dark', {
      backgroundColor: '#308fe8',
    }),
  },
}));

export default function CustomizedProgressBars() {

  const { studentUploadStatus, studentValStatus, setStudentValStatus, isCheckedStudentBox, setIsCheckedStudentBox }  = useUpload();
  

  const showStudentValStatus= { 
    checking: 'Validating student data...',
    complete: 'Validated: student data is complete and correctly formatted.',
    error: 'FAILURE: couldn\'t validate student data. Data missing or in wrong format.',
  }[studentValStatus]; 

  const [valProgress, setValProgress] = useState(0); 

  useEffect(() => {
        if (studentUploadStatus !== "complete") return; 
        if (studentValStatus == "complete") return;
        const timer = setInterval(() => {
          setValProgress((oldProgress) => {
            const diff = Math.random() * 15;
            const next = Math.min(oldProgress + diff, 100);
            if (next === 100) {
              setTimeout(() => {
                  clearInterval(timer);
              }, 2000);
              setStudentValStatus('complete');
            }
            return next; 
            
          });
        }, 500);
          return () => {
            clearInterval(timer);
          };
        }, [studentUploadStatus]);
  
  
    return (
      <Box sx = {{position: 'relative', width:'100%'}}>
      <BorderLinearProgress variant="determinate" value={valProgress}/>
      <Typography 
          variant='body2' 
          color ='#f2cd00'
          sx={{
              position: 'absolute',
              textAlign: 'center',
              top: '50%', 
              left: '-50%',
              right:0,
              transform: 'translateY(-50%)', 
          }}        
      >
          {showStudentValStatus}
      </Typography>
      <Box sx={{width:'20%', height:'20%'}}>
        <Checkbox 
          disabled
          checked={isCheckedStudentBox}
          sx={{
              position: 'absolute',
              //textAlign: 'right',
              top: '0%',
              left:'51.5%',
              width: '100%',
              height: '100%',
              '& .MuiSvgIcon-root': {
                  fontSize: '100px',
                  color:'#007c41',           
              },
                "&.MuiCheckbox-root": {
                  borderRadius: 0,
                  width:'6%',
                  padding: 0,
              },
              "&.Mui-checked": {
                  backgroundColor: "#f2cd00",
                  width:'6%',
              },
            }}>       
          </Checkbox>
        </Box>
      </Box>
    );
  }
