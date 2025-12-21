import React, { useEffect, useState } from 'react';
import { Typography } from '@mui/material';
import LinearProgress from '@mui/material/LinearProgress';
import { useUpload } from '../components/UploadContext';

export default function Running() {
  const { studentUploadData, teacherUploadData } = useUpload(); 
  const [totalVal, setTotalVal] = useState(0); 
  const [failedVal, setFailedVal] = useState(0); 
  const [processedVal, setProcessedVal] = useState(0); 
  const [notFoundVal, setNotFoundVal] = useState(0); 
          
  useEffect(() => {
    const studentsTeachersArray = { studentUploadData, teacherUploadData};
    const jsonAllData = JSON.stringify(studentsTeachersArray); 

    fetch('/matches', { 
      method: 'POST', 
      headers: { 
        'Content-Type': 'application/json'
      }, 
      body: jsonAllData
    })
    .then(response => response.json())
    .then(data => console.log(data))
    .catch(error => console.error('Error: ', error)); 

  }, []);

  useEffect(() => { 
    fetch('/running', { 
      method: 'GET', 
      headers: { 
        'Content-Type': 'application/json'
      }, 
      body: jsonAllData
    })
    .then(response => response.JSON)
    .then(console.log(data))
    .catch(error => console.error('Error: ', error))
  }, []); 

  return (
      <Typography variant="h4"></Typography>
    );
}
