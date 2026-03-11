import React, { useState } from 'react';
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

// const theme = createMuiTheme({
//   palette: {
//     primary: {
//       main: '#007c41', 
//       gold: '#f2cd00'
//     },
//   },
//   overrides: {
//     MuiButton: {
//       raisedPrimary: {
//         color: 'white',
//       },
//     },
//   }
// });

// const { palette } = createTheme();
// const { augmentColor } = palette;
// const createColor = (mainColor) => augmentColor({ color: { main: mainColor } });
// const theme = createTheme({
//   palette: {
//     UAGreen: createColor('#007c41'),
//     UAGold: createColor('#f2cd00')  },
// });

export default function StudentFileUpload() {
  const [loading, setLoading] = useState(false);
  const { studentUploadStatus, setStudentUploadData, setStudentUploadStatus } = useUpload();

  function handleUpload(event) {
    const file = event.target.files[0];
    if (!file) {
      console.log('No file selected.');
      return;
    }

    setLoading(true);
    setStudentUploadStatus('uploading');

    setTimeout(() => {
      Papa.parse(file, {
        header: true,
        dynamicTyping: true,
        skipEmptyLines: true,
        complete: (results) => {
          const parsed = results.data.map((row) => ({
            ...row,
            specialtyInterests: typeof row.specialtyInterests === 'string' ? JSON.parse(row.specialtyInterests) : row.specialtyInterests,
            weightedPreferences: typeof row.weightedPreferences === 'string' ? JSON.parse(row.weightedPreferences) : row.weightedPreferences,
          }));
          setStudentUploadData(parsed);
          console.log(parsed);
          setStudentUploadStatus('complete');
          setLoading(false);
        },
        error: (err) => {
          console.error('Upload failed:', err);
          setStudentUploadStatus('error');
          setLoading(false);
        },
      });
    }, 3000);
  }  

  const buttonLabel = {
    initial: 'Upload Students',
    uploading: 'Uploading...',
    complete: 'Upload Complete',
    error: 'Upload Failed',
  }[studentUploadStatus];

  const buttonIcon = {
    initial: <CloudUploadIcon/>,
    uploading: null,
    complete: <CheckBoxIcon/>
  }[studentUploadStatus];

  const buttonColor = {
    initial: 'green', 
    uploading: 'black', 
    complete: 'gold'
  }[studentUploadStatus]; 

  const buttonBackground = {
    complete: 'green'
  }[studentUploadStatus];

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
