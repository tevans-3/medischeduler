import {useState, useContext, useEffect } from 'react';
import { styled } from '@mui/material/styles';
import Button from '@mui/material/Button';
import { PlayCircleFilled } from '@mui/icons-material';
import LinearProgress, { linearProgressClasses } from '@mui/material/LinearProgress';
import { AppBar, Toolbar, Typography, Drawer, List, ListItem, ListItemText, Box, Stack } from '@mui/material';
import Checkbox from '@mui/material/Checkbox';
import theme from '../theme';
import { useNavigate } from 'react-router-dom';
import { useUpload } from './UploadContext'; 

const VisuallyHiddenInput = styled('input')({
  position: 'absolute',
  width: 1,
  height: 1,
  padding: 0,
  margin: -1,
  overflow: 'hidden',
  clip: 'rect(0,0,0,0)',
  whiteSpace: 'nowrap',
  border: 0,
});

export default function RunScheduler(){
    const [isDisabled, setIsDisabled] = useState(true); 

    const { teacherValStatus, studentValStatus } = useUpload();

    const valStatusKey = `${studentValStatus}_${teacherValStatus}`;
    const buttonLabel= {  
        complete_complete: 'checked',
    }[valStatusKey];

    const navigate = useNavigate(); 

    useEffect(() => {
    if (valStatusKey === 'complete_complete') {
        setIsDisabled(false);
    } else {
        setIsDisabled(true);
    }
    }, [valStatusKey]);

    const buttonColor = {
        complete_complete: 'gold', 
    }[valStatusKey]; 

    const buttonBackground = {
        complete_complete: 'green'
    }[valStatusKey];


    return(
        <Box>
        <Button
            component="label"
            role={undefined}
            variant="contained"
            tabIndex={-1}
            hover={{color:theme.palette.common.black}

            }
            //startIcon={<PlayCircleFilled/>}
            color={theme.palette.common.black}
            disabled={isDisabled}
            sx={{color:buttonColor, 
                backgroundColor:buttonBackground, 
                height:75, 
                fontWeight:'bold',
                fontSize:'20px',
                width: '50%',
                '& .MuiSvgIcon-root': { fontSize: 40 }
             }}
            onClick={()=> navigate('/running')}
        >
            Run Scheduler
        </Button>
        </Box>
    );
}