import React, { useState, useContext } from 'react';
import { styled } from '@mui/material/styles';
import Button from '@mui/material/Button';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import Papa from 'papaparse';
import theme from '../theme';
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

export default function TeacherFileUpload() {
  const [loading, setLoading] = useState(false);
  const { teacherUploadStatus, setTeacherUploadData, setTeacherUploadStatus } = useUpload();

  function handleUpload(event) {
    const file = event.target.files[0];
    
    if (!file) {
      console.log('No file selected.');
      return;
    }

    setLoading(true);
    setTeacherUploadStatus('uploading');
    console.log('File name:', file.name);
      console.log('File type:', file.type);

    setTimeout(() => {
      Papa.parse(file, {
        header: true,
        dynamicTyping: true,
        skipEmptyLines: true,
        complete: (results) => {
          setTeacherUploadData(results.data);
          console.log(results.data);
          setTeacherUploadStatus('complete');
          setLoading(false);
        },
        error: (err) => {
          console.error('Upload failed:', err);
          setTeacherUploadStatus('error');
          setLoading(false);
        },
      });
    }, 3000);
  }  

  const buttonLabel = {
    initial: 'Upload Teachers',
    uploading: 'Uploading...',
    complete: 'Upload Complete',
    error: 'Upload Failed',
  }[teacherUploadStatus];

  const buttonIcon = {
    initial: <CloudUploadIcon/>,
    uploading: null,
    complete: <CheckBoxIcon/>
  }[teacherUploadStatus];

  const buttonColor = {
    initial: 'green', 
    uploading: 'black', 
    complete: 'gold'
  }[teacherUploadStatus]; 

  const buttonBackground = {
    complete: 'green'
  }[teacherUploadStatus];

  return (
    <Button
      component="label"
      role={undefined}
      variant="contained"
      tabIndex={-1}
      startIcon={buttonIcon}
      color={theme.palette.common.black}
      disabled={loading}
      sx={{color:buttonColor, backgroundColor:buttonBackground}}
    >
      {buttonLabel}
      <VisuallyHiddenInput type="file" onChange={handleUpload} />
    </Button>
  );
}
